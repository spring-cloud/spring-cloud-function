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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.jar.Manifest;

import com.microsoft.azure.functions.ExecutionContext;

import org.reactivestreams.Publisher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * @author Soby Chacko
 */
public class AzureSpringFunctionInitializer implements Closeable {

	private volatile Function<Publisher<?>, Publisher<?>> function;

	private AtomicBoolean initialized = new AtomicBoolean();

	private final Class<?> configurationClass;

	@Autowired(required = false)
	private volatile FunctionCatalog catalog;

	@Autowired(required = false)
	private volatile FunctionInspector inspector;

	private volatile static ConfigurableApplicationContext context;

	public AzureSpringFunctionInitializer(Class<?> configurationClass) {
		this.configurationClass = configurationClass == null ? getClass()
				: configurationClass;
	}

	public AzureSpringFunctionInitializer() {
		this(getStartClass());
	}

	@Override
	public void close() throws IOException {
		if (AzureSpringFunctionInitializer.context != null) {
			AzureSpringFunctionInitializer.context.close();
			AzureSpringFunctionInitializer.context = null;
		}
	}

	@SuppressWarnings("unchecked")
	protected void initialize(ExecutionContext ctxt) {

		ConfigurableApplicationContext context = AzureSpringFunctionInitializer.context;

		if (!this.initialized.compareAndSet(false, true)) {
			return;
		}
		if (ctxt != null) {
			ctxt.getLogger().info("Initializing functions");
		}

		if (context == null) {
			synchronized (AzureSpringFunctionInitializer.class) {
				if (context == null) {
					ClassUtils.overrideThreadContextClassLoader(
							AzureSpringFunctionInitializer.class.getClassLoader());
					context = springApplication().run();
					AzureSpringFunctionInitializer.context = context;
				}
			}

		}

		context.getAutowireCapableBeanFactory().autowireBean(this);
		if (ctxt != null) {
			ctxt.getLogger().info("Initialized context: catalog=" + this.catalog);
		}
		String name = context.getEnvironment().getProperty("function.name");

		if (name == null) {
			name = "function";
		}
		if (this.catalog == null) {
			if (context.containsBean(name)) {
				if (ctxt != null) {
					ctxt.getLogger()
							.info("No catalog. Looking for Function bean name=" + name);
				}
				this.function = context.getBean(name, Function.class);
			}
		}
		else {
			Set<String> functionNames = this.catalog.getNames(Function.class);
			if (functionNames.size() == 1) {
				this.function = this.catalog.lookup(Function.class,
						functionNames.iterator().next());
			}
			else {
				this.function = this.catalog.lookup(Function.class, name);
			}
		}
	}

	private SpringApplication springApplication() {
		Class<?> sourceClass = configurationClass;
		SpringApplication application = new org.springframework.cloud.function.context.FunctionalSpringApplication(
				sourceClass);
		application.setWebApplicationType(WebApplicationType.NONE);
		return application;
	}

	protected boolean isSingleInput(Function<?, ?> function, Object input) {
		if (!(input instanceof Collection)) {
			return true;
		}
		if (this.inspector != null) {
			return Collection.class.isAssignableFrom(inspector.getInputType(function));
		}
		return ((Collection<?>) input).size() <= 1;
	}

	protected boolean isSingleOutput(Function<?, ?> function, Object output) {
		if (!(output instanceof Collection)) {
			return true;
		}
		if (this.inspector != null) {
			return Collection.class.isAssignableFrom(inspector.getOutputType(function));
		}
		return ((Collection<?>) output).size() <= 1;
	}

	protected Function<Publisher<?>, Publisher<?>> lookup(String name) {
		Function<Publisher<?>, Publisher<?>> function = this.function;
		if (name != null && this.catalog != null) {
			Function<Publisher<?>, Publisher<?>> preferred = this.catalog
					.lookup(Function.class, name);
			if (preferred != null) {
				function = preferred;
			}
		}
		if (function != null) {
			return function;
		}
		throw new IllegalStateException("No function defined with name=" + name);
	}

	private static Class<?> getStartClass() {
		ClassLoader classLoader = org.springframework.cloud.function.adapter.azure.AzureSpringFunctionInitializer.class
				.getClassLoader();
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
								org.springframework.cloud.function.adapter.azure.AzureSpringFunctionInitializer.class
										.getClassLoader());
						SpringBootApplication declaredAnnotation = aClass
								.getDeclaredAnnotation(SpringBootApplication.class);
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

	public Function<Publisher<?>, Publisher<?>> getFunction() {
		return function;
	}

	public FunctionCatalog getCatalog() {
		return catalog;
	}

	public FunctionInspector getInspector() {
		return inspector;
	}
}
