/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.Manifest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.cloud.function.context.AbstractSpringFunctionAdapterInitializer;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * @author Dave Syer
 * @author Semyon Fishman
 * @deprecated as of 2.1 in favor of {@link AbstractSpringFunctionAdapterInitializer}.
 * It is no longer used by the framework and only exists for avoiding potential regressions.
 */
@Deprecated
public class SpringFunctionInitializer implements Closeable {

	private static Log logger = LogFactory.getLog(SpringFunctionInitializer.class);

	private final Class<?> configurationClass;

	private Function<Publisher<?>, Publisher<?>> function;

	private Consumer<Publisher<?>> consumer;

	private Supplier<Publisher<?>> supplier;

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

	@Override
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	protected void initialize() {
		if (!this.initialized.compareAndSet(false, true)) {
			return;
		}
		logger.info("Initializing: " + this.configurationClass);
		SpringApplication builder = springApplication();
		ConfigurableApplicationContext context = builder.run();
		context.getAutowireCapableBeanFactory().autowireBean(this);
		this.context = context;
		if (this.catalog == null) {
			initFunctionConsumerOrSupplierFromContext();
		}
		else {
			initFunctionConsumerOrSupplierFromCatalog();
		}
	}

	private String resolveName(Class<?> type) {
		String functionName = context.getEnvironment().getProperty("function.name");
		if (functionName != null) {
			return functionName;
		}
		else if (type.isAssignableFrom(Function.class)) {
			return "function";
		}
		else if (type.isAssignableFrom(Consumer.class)) {
			return "consumer";
		}
		else if (type.isAssignableFrom(Supplier.class)) {
			return "supplier";
		}
		throw new IllegalStateException("Unknown type " + type);
	}

	@SuppressWarnings("unchecked")
	private void initFunctionConsumerOrSupplierFromContext() {
		String name = resolveName(Function.class);
		if (context.containsBean(name) && context.getBean(name) instanceof Function) {
			this.function = context.getBean(name, Function.class);
			return;
		}

		name = resolveName(Consumer.class);
		if (context.containsBean(name) && context.getBean(name) instanceof Consumer) {
			this.consumer = context.getBean(name, Consumer.class);
			return;
		}

		name = resolveName(Supplier.class);
		if (context.containsBean(name) && context.getBean(name) instanceof Supplier) {
			this.supplier = context.getBean(name, Supplier.class);
			return;
		}
	}

	private void initFunctionConsumerOrSupplierFromCatalog() {
		String name = resolveName(Function.class);
		this.function = this.catalog.lookup(Function.class, name);
		if (this.function != null) {
			return;
		}

		name = resolveName(Consumer.class);
		this.consumer = this.catalog.lookup(Consumer.class, name);
		if (this.consumer != null) {
			return;
		}

		name = resolveName(Supplier.class);
		this.supplier = this.catalog.lookup(Supplier.class, name);
		if (this.supplier != null) {
			return;
		}

		if (this.catalog.size() == 1) {
			Iterator<String> names = this.catalog.getNames(Function.class).iterator();
			if (names.hasNext()) {
				this.function = this.catalog.lookup(Function.class, names.next());
				return;
			}

			names = this.catalog.getNames(Consumer.class).iterator();
			if (names.hasNext()) {
				this.consumer = this.catalog.lookup(Consumer.class, names.next());
				return;
			}

			names = this.catalog.getNames(Supplier.class).iterator();
			if (names.hasNext()) {
				this.supplier = this.catalog.lookup(Supplier.class, names.next());
				return;
			}
		}
	}

	private SpringApplication springApplication() {
		Class<?> sourceClass = this.configurationClass;
		SpringApplication application = new org.springframework.cloud.function.context.FunctionalSpringApplication(
				sourceClass);
		application.setWebApplicationType(WebApplicationType.NONE);
		return application;
	}

	protected Class<?> getInputType() {
		if (this.inspector != null) {
			return this.inspector.getInputType(function());
		}
		return Object.class;
	}

	protected Object function() {
		if (this.function != null) {
			return this.function;
		}
		else if (this.consumer != null) {
			return this.consumer;
		}
		else if (this.supplier != null) {
			return this.supplier;
		}
		return null;
	}

	protected boolean acceptsInput() {
		return !this.inspector.getInputType(function()).equals(Void.class);
	}

	protected boolean returnsOutput() {
		return !this.inspector.getOutputType(function()).equals(Void.class);
	}

	protected Publisher<?> apply(Publisher<?> input) {
		if (this.function != null) {
			return Flux.from(this.function.apply(input));
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

}
