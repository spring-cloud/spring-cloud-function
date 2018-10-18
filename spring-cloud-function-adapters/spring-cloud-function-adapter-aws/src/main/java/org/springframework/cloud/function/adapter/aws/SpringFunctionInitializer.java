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

package org.springframework.cloud.function.adapter.aws;

import java.io.Closeable;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.jar.Manifest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 */
public class SpringFunctionInitializer implements Closeable {

	private static Log logger = LogFactory.getLog(SpringFunctionInitializer.class);

	private final Class<?> configurationClass;

	private Function<Publisher<?>, Publisher<?>> function;

	private AtomicBoolean initialized = new AtomicBoolean();

	@Autowired(required = false)
	private FunctionInspector inspector;

	@Autowired(required = false)
	private FunctionCatalog catalog;

	private ConfigurableApplicationContext context;

	public SpringFunctionInitializer(Class<?> configurationClass) {
		this.configurationClass = configurationClass;
	}

	public SpringFunctionInitializer() {
		this(getStartClass());
	}

	@Override
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@SuppressWarnings("unchecked")
	protected void initialize() {
		if (!this.initialized.compareAndSet(false, true)) {
			return;
		}
		logger.info("Initializing: " + configurationClass);
		SpringApplication builder = springApplication();
		ConfigurableApplicationContext context = builder.run();
		context.getAutowireCapableBeanFactory().autowireBean(this);
		String name = context.getEnvironment().getProperty("function.name");
		if (name == null) {
			name = "function";
		}
		if (this.catalog == null) {
			if (context.containsBean(name)) {
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
		this.context = context;

	}

	private SpringApplication springApplication() {
		Class<?> sourceClass = configurationClass;
		SpringApplication application = new org.springframework.cloud.function.context.FunctionalSpringApplication(
				sourceClass);
		application.setWebApplicationType(WebApplicationType.NONE);
		return application;
	}

	protected Class<?> getInputType() {
		if (inspector != null) {
			return inspector.getInputType(function());
		}
		return Object.class;
	}

	protected Object function() {
		return this.function;
	}

	protected Publisher<?> apply(Publisher<?> input) {
		if (this.function != null) {
			return Flux.from(function.apply(input));
		}
		throw new IllegalStateException("No function defined");
	}

	private static Class<?> getStartClass() {
		ClassLoader classLoader = SpringFunctionInitializer.class.getClassLoader();
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
			logger.info("Main class: " + result);
			return result;
		}
		catch (Exception ex) {
			logger.error("Failed to find main class", ex);
			return null;
		}
	}

	private static Class<?> getStartClass(List<URL> list) {
		logger.info("Searching manifests: " + list);
		for (URL url : list) {
			try {
				logger.info("Searching manifest: " + url);
				InputStream inputStream = url.openStream();
				try {
					Manifest manifest = new Manifest(inputStream);
					String startClass = manifest.getMainAttributes()
							.getValue("Start-Class");
					if (startClass != null) {
						return ClassUtils.forName(startClass,
								SpringFunctionInitializer.class.getClassLoader());
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
