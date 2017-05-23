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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.Manifest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionInspector;
import org.springframework.cloud.function.registry.FunctionCatalog;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 */
public class SpringFunctionInitializer implements Closeable {

	private static Log logger = LogFactory.getLog(SpringFunctionInitializer.class);

	private final Class<?> configurationClass;

	private Function<Flux<?>, Flux<?>> function;

	private Consumer<Flux<?>> consumer;

	private Supplier<Flux<?>> supplier;

	private AtomicBoolean initialized = new AtomicBoolean();

	@Autowired(required = false)
	private FunctionInspector inspector;

	@Autowired(required = false)
	private FunctionCatalog catalog;

	private String name;

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
		SpringApplicationBuilder builder = new SpringApplicationBuilder(
				configurationClass);
		ConfigurableApplicationContext context = builder.web(false).run();
		context.getAutowireCapableBeanFactory().autowireBean(this);
		String name = context.getEnvironment().getProperty("function.name");
		boolean defaultName = false;
		if (name == null) {
			name = "function";
			defaultName = true;
		}
		if (this.catalog == null) {
			this.function = context.getBean(name, Function.class);
		}
		else {
			this.function = this.catalog.lookupFunction(name);
			this.name = name;
			if (this.function == null) {
				if (defaultName) {
					name = "consumer";
				}
				this.consumer = this.catalog.lookupConsumer(name);
				this.name = name;
				if (this.consumer == null) {
					if (defaultName) {
						name = "supplier";
					}
					this.supplier = this.catalog.lookupSupplier(name);
					this.name = name;
				}
			}
		}
		this.context = context;
	}

	protected Class<?> getInputType() {
		if (inspector != null) {
			return inspector.getInputType(this.name);
		}
		return Object.class;
	}

	protected Flux<?> apply(Flux<?> input) {
		if (this.function != null) {
			return function.apply(input);
		}
		if (this.consumer != null) {
			this.consumer.accept(input);
			return Flux.empty();
		}
		if (this.supplier != null) {
			return this.supplier.get();
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
