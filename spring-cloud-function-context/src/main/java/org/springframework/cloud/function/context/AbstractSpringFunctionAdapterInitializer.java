/*
 * Copyright 2019-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.context;

import java.io.Closeable;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
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
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.context.config.FunctionContextUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

/**
 * Base implementation for  adapter initializers and request handlers.
 *
 * @param <C> the type of the target specific (native) context object.
 *
 * @author Oleg Zhurakousky
 * @since 2.1
 */
public abstract class AbstractSpringFunctionAdapterInitializer<C> implements Closeable {

	private static Log logger = LogFactory.getLog(AbstractSpringFunctionAdapterInitializer.class);

	/**
	 * Name of the bean for registering the target execution context passed to `initialize(context)` operation.
	 */
	public static final String TARGET_EXECUTION_CTX_BEAN_NAME = "targetExecutionContext";

	private final Class<?> configurationClass;

	private Function<Publisher<?>, Publisher<?>> function;

	private Consumer<Publisher<?>> consumer;

	private Supplier<Publisher<?>> supplier;

	private FunctionRegistration<?> functionRegistration;

	private AtomicBoolean initialized = new AtomicBoolean();

	@Autowired(required = false)
	private FunctionInspector inspector;

	@Autowired(required = false)
	private FunctionCatalog catalog;

	private ConfigurableApplicationContext context;

	public ConfigurableApplicationContext getContext() {
		return context;
	}

	public AbstractSpringFunctionAdapterInitializer(Class<?> configurationClass) {
		Assert.notNull(configurationClass, "'configurationClass' must not be null");
		this.configurationClass = configurationClass;
	}

	public AbstractSpringFunctionAdapterInitializer() {
		this(getStartClass());
	}

	@Override
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	protected void initialize(C targetContext) {
		if (!this.initialized.compareAndSet(false, true)) {
			return;
		}
		logger.info("Initializing: " + this.configurationClass);
		SpringApplication builder = springApplication();

		this.registerTargetContext(targetContext, builder);
		ConfigurableApplicationContext context = builder.run();
		context.getAutowireCapableBeanFactory().autowireBean(this);
		this.context = context;
		if (this.catalog == null) {
			initFunctionConsumerOrSupplierFromContext(targetContext);
		}
		else {
			initFunctionConsumerOrSupplierFromCatalog(targetContext);
		}
	}

	private void registerTargetContext(C targetContext, SpringApplication builder) {
		if (targetContext != null) {
			builder.addInitializers(new ApplicationContextInitializer<ConfigurableApplicationContext>() {
				@SuppressWarnings("unchecked")
				@Override
				public void initialize(ConfigurableApplicationContext applicationContext) {
					((GenericApplicationContext) applicationContext).registerBean(TARGET_EXECUTION_CTX_BEAN_NAME,
							(Class<C>) targetContext.getClass(), (Supplier<C>) () -> targetContext);
				}
			});
		}
	}

	protected FunctionInspector getInspector() {
		return inspector;
	}

	protected Class<?> getInputType() {
		if (this.inspector != null) {
			return this.inspector.getInputType(function());
		}
		else if (functionRegistration != null) {
			return functionRegistration.getType().getInputType();
		}
		return Object.class;
	}

	protected Function<Publisher<?>, Publisher<?>> getFunction() {
		return function;
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

	/**
	 * Allows you to resolve function name for cases where it
	 * could not be located under default name.
	 *
	 * Default implementation returns empty string.
	 *
	 * @param targetContext the target context instance
	 * @return the name of the function
	 */
	protected String doResolveName(Object targetContext) {
		return "";
	}

	protected <O> O result(Object input, Publisher<?> output) {
		List<Object> result = new ArrayList<>();
		for (Object value : Flux.from(output).toIterable()) {
			result.add(value);
		}
		if (isSingleInput(getFunction(), input) && result.size() == 1) {
			@SuppressWarnings("unchecked")
			O value = (O) result.get(0);
			return value;
		}
		if (isSingleOutput(getFunction(), input) && result.size() == 1) {
			@SuppressWarnings("unchecked")
			O value = (O) result.get(0);
			return value;
		}
		@SuppressWarnings("unchecked")
		O value = (O) result;
		return CollectionUtils.isEmpty(result) ? null : value;
	}

	private boolean isSingleInput(Function<?, ?> function, Object input) {
		if (!(input instanceof Collection)) {
			return true;
		}
		if (getInspector() != null) {
			return Collection.class
					.isAssignableFrom(getInspector().getInputType(function));
		}
		return ((Collection<?>) input).size() <= 1;
	}

	private boolean isSingleOutput(Function<?, ?> function, Object output) {
		if (!(output instanceof Collection)) {
			return true;
		}
		if (getInspector() != null) {
			return Collection.class
					.isAssignableFrom(getInspector().getOutputType(function));
		}
		return ((Collection<?>) output).size() <= 1;
	}

	private String resolveName(Class<?> type, Object targetContext) {
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

	private static Class<?> getStartClass() {
		ClassLoader classLoader = AbstractSpringFunctionAdapterInitializer.class.getClassLoader();
		Class<?> mainClass = null;
		if (System.getenv("MAIN_CLASS") != null) {
			mainClass = ClassUtils.resolveClassName(System.getenv("MAIN_CLASS"), classLoader);
		}
		if (System.getProperty("MAIN_CLASS") != null) {
			mainClass = ClassUtils.resolveClassName(System.getProperty("MAIN_CLASS"), classLoader);
		}
		else {
			try {
				Class<?> result = getStartClass(
						Collections.list(classLoader.getResources("META-INF/MANIFEST.MF")));
				if (result == null) {
					result = getStartClass(Collections
							.list(classLoader.getResources("meta-inf/manifest.mf")));
				}
				Assert.notNull(result, "Failed to locate main class");
				mainClass = result;
			}
			catch (Exception ex) {
				throw new IllegalStateException("Failed to discover main class. An attempt was made to discover "
						+ "main class as 'MAIN_CLASS' environment variable, system property as well as "
						+ "entry in META-INF/MANIFEST.MF (in that order).", ex);
			}
		}
		logger.info("Main class: " + mainClass);
		return mainClass;
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
								AbstractSpringFunctionAdapterInitializer.class.getClassLoader());
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


	@SuppressWarnings({ "unchecked", "rawtypes" })
	private <T> T getAndInstrumentFromContext(String name) {
		this.functionRegistration =
				new FunctionRegistration(context.getBean(name), name);

		Type type = FunctionContextUtils.
				findType(name, (ConfigurableListableBeanFactory) this.context.getBeanFactory());

		this.functionRegistration = functionRegistration.type(new FunctionType(type)).wrap();

		return (T) functionRegistration.getTarget();
	}

	private void initFunctionConsumerOrSupplierFromContext(Object targetContext) {
		String name = resolveName(Function.class, targetContext);
		if (context.containsBean(name) && context.getBean(name) instanceof Function) {
			this.function = getAndInstrumentFromContext(name);
			return;
		}

		name = resolveName(Consumer.class, targetContext);
		if (context.containsBean(name) && context.getBean(name) instanceof Consumer) {
			this.function = getAndInstrumentFromContext(name); // FluxConsumer or any other consumer wrapper is a Function
			return;
		}

		name = resolveName(Supplier.class, targetContext);
		if (context.containsBean(name) && context.getBean(name) instanceof Supplier) {
			this.supplier = getAndInstrumentFromContext(name);
			return;
		}
	}

	private void initFunctionConsumerOrSupplierFromCatalog(Object targetContext) {
		String name = resolveName(Function.class, targetContext);
		this.function = this.catalog.lookup(Function.class, name);
		if (this.function != null) {
			return;
		}

		name = resolveName(Consumer.class, targetContext);
		this.consumer = this.catalog.lookup(Consumer.class, name);
		if (this.consumer != null) {
			return;
		}

		name = resolveName(Supplier.class, targetContext);
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
		else {
			name = this.doResolveName(targetContext);
			this.function = this.catalog.lookup(Function.class, name);
			if (this.function != null) {
				return;
			}

			this.consumer = this.catalog.lookup(Consumer.class, name);
			if (this.consumer != null) {
				return;
			}
			this.supplier = this.catalog.lookup(Supplier.class, name);
			if (this.supplier != null) {
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
}
