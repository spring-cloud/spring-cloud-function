/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.adapter.azure;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.jar.Manifest;

import com.microsoft.azure.serverless.functions.ExecutionContext;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.core.FunctionCatalog;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * @author Soby Chacko
 */
public class AzureSpringFunctionInitializer implements Closeable {

	private Function<Flux<?>, Flux<?>> function;

	private AtomicBoolean initialized = new AtomicBoolean();

	private  Class<?> configurationClass;

	@Autowired(required = false)
	private FunctionCatalog catalog;

	private static ConfigurableApplicationContext context;

	public AzureSpringFunctionInitializer(Class<?> configurationClass) {
		this.configurationClass = configurationClass;
	}

	public AzureSpringFunctionInitializer() {
		this(getStartClass());
	}


	@Override
	public void close() throws IOException {
		if (AzureSpringFunctionInitializer.context != null) {
			AzureSpringFunctionInitializer.context.close();
		}
	}

	@SuppressWarnings("unchecked")
	protected void initialize(ExecutionContext ctxt) {

		ConfigurableApplicationContext context = AzureSpringFunctionInitializer.context;
		
		if (!this.initialized.compareAndSet(false, true)) {
			return;
		}
		ctxt.getLogger().info("Initializing function");
		
		if (context==null) {
			synchronized (AzureSpringFunctionInitializer.class) {
				if (context==null) {
					SpringApplicationBuilder builder = new SpringApplicationBuilder(
							configurationClass);
					ClassUtils.overrideThreadContextClassLoader(AzureSpringFunctionInitializer.class.getClassLoader());

					context = builder.web(false).run();
					AzureSpringFunctionInitializer.context = context;
				}
			}
			
		}

		context.getAutowireCapableBeanFactory().autowireBean(this);
		String name = context.getEnvironment().getProperty("function.name");

		if (name == null) {
			name = "function";
		}
		if (this.catalog == null) {
			this.function = context.getBean(name, Function.class);
		}
		else {
			Set<String> functionNames = this.catalog.getFunctionNames();
			this.function = this.catalog.lookupFunction(functionNames.iterator().next());
		}
	}

	protected Flux<?> apply(Flux<?> input) {
		if (this.function != null) {
			return function.apply(input);
		}
		throw new IllegalStateException("No function defined");
	}

	private static Class<?> getStartClass() {
		ClassLoader classLoader = org.springframework.cloud.function.adapter.azure.AzureSpringFunctionInitializer.class.getClassLoader();
		if (System.getenv("MAIN_CLASS") != null) {
			return ClassUtils.resolveClassName(System.getenv("MAIN_CLASS"), classLoader);
		}
		try {
			Class<?> result = getStartClass(
					Collections.list(classLoader.getResources("META-INF/MANIFEST.MF")));
			if (result == null) {
				result = getStartClass(Collections
						.list(classLoader.getResources("meta-inf/manifest.mf")));
			}
			return result;
		}
		catch (Exception ex) {
			return null;
		}
	}

	private static Class<?> getStartClass(List<URL> list) {
		for (URL url : list) {
			try {
				InputStream inputStream = url.openStream();
				try {
					Manifest manifest = new Manifest(inputStream);
					String startClass = manifest.getMainAttributes()
							.getValue("Main-Class");
					if (startClass != null) {
						Class<?> aClass = ClassUtils.forName(startClass,
								org.springframework.cloud.function.adapter.azure.AzureSpringFunctionInitializer.class.getClassLoader());
						SpringBootApplication declaredAnnotation = aClass.getDeclaredAnnotation(SpringBootApplication.class);
						if (declaredAnnotation != null) {
							return aClass;
						}
					}
				}
				finally {
					inputStream.close();
				}
			}
			catch (Exception ex) {
			}
		}
		return null;
	}
}
