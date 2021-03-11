/*
 * Copyright 2019-2021 the original author or authors.
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
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.config.FunctionContextUtils;
import org.springframework.cloud.function.context.config.JsonMessageConverter;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.cloud.function.context.config.SmartCompositeMessageConverter;
import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.cloud.function.utils.FunctionClassUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Base implementation for  adapter initializers and request handlers.
 *
 * @param <C> the type of the target specific (native) context object.
 *
 * @author Oleg Zhurakousky
 * @since 2.1
 *
 * @deprecated since 3.1 in favor of individual implementations of invokers
 */
@SuppressWarnings("rawtypes")
@Deprecated
public abstract class AbstractSpringFunctionAdapterInitializer<C> implements Closeable {

	private static Log logger = LogFactory.getLog(AbstractSpringFunctionAdapterInitializer.class);

	/**
	 * Name of the bean for registering the target execution context passed to `initialize(context)` operation.
	 */
	public static final String TARGET_EXECUTION_CTX_NAME = "executionContext";

	private final Class<?> configurationClass;

	private Function function;

	private Consumer consumer;

	private Supplier supplier;

	private FunctionRegistration<?> functionRegistration;

	private AtomicBoolean initialized = new AtomicBoolean();

	@Autowired(required = false)
	protected FunctionCatalog catalog;

	@Autowired(required = false)
	protected JsonMapper jsonMapper;

	private ConfigurableApplicationContext context;

	public ConfigurableApplicationContext getContext() {
		return context;
	}

	public AbstractSpringFunctionAdapterInitializer(Class<?> configurationClass) {
		Assert.notNull(configurationClass, "'configurationClass' must not be null");
		this.configurationClass = configurationClass;
	}

	public AbstractSpringFunctionAdapterInitializer() {
		this(FunctionClassUtils.getStartClass());
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
		ConfigurableApplicationContext context = builder.run();
		context.getAutowireCapableBeanFactory().autowireBean(this);
		this.context = context;
		if (this.catalog == null) {
			SmartCompositeMessageConverter messageConverter =
					new SmartCompositeMessageConverter(Collections.singletonList(new JsonMessageConverter(new JacksonMapper(new ObjectMapper()))));
			this.catalog = new SimpleFunctionRegistry(new GenericConversionService(),
					messageConverter, new JacksonMapper(new ObjectMapper()));
			initFunctionConsumerOrSupplierFromContext(targetContext);
		}
		else {
			initFunctionConsumerOrSupplierFromCatalog(targetContext);
		}
	}

	protected Class<?> getInputType() {

		Object func =  function();
		if (func != null && func instanceof FunctionInvocationWrapper) {
			return FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(((FunctionInvocationWrapper) func).getInputType()));
		}
		if (functionRegistration != null) {
			return functionRegistration.getType().getInputType();
		}
		return Object.class;
	}

	@SuppressWarnings("unchecked")
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

	@SuppressWarnings("unchecked")
	protected Publisher<?> apply(Publisher<?> input) {
		if (this.function != null) {
			Object result = this.function.apply(input);
			if (result instanceof Publisher) {
				return Flux.from((Publisher) result);
			}
			else {
				return Flux.just(result);
			}
		}
		if (this.consumer != null) {
			this.consumer.accept(input);
			return Flux.empty();
		}
		if (this.supplier != null) {
			Object result = this.supplier.get();
			if (!(result instanceof Publisher)) {
				result = Mono.just(result);
			}
			return (Publisher<?>) result;
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

	protected Object convertOutput(Object input, Object output) {
		return output;
	}

	protected <O> O result(Object input, Publisher<?> output) {
		List<Object> result = new ArrayList<>();
		for (Object value : Flux.from(output).toIterable()) {
			result.add(this.convertOutput(input, value));
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

	protected void clear(String name) {
		FunctionInvocationWrapper f = this.catalog.lookup(name);
		if (f.isFunction()) {
			this.function = f;
		}
		else if (f.isConsumer()) {
			this.consumer = f;
		}
		else {
			this.supplier = f;
		}
	}

	private boolean isSingleInput(Function<?, ?> function, Object input) {
		if (!(input instanceof Collection)) {
			return true;
		}

		if (function != null) {
			return Collection.class
					.isAssignableFrom(((FunctionInvocationWrapper) function).getRawInputType());
		}
		return ((Collection<?>) input).size() <= 1;
	}

	private boolean isSingleOutput(Function<?, ?> function, Object output) {
		if (!(output instanceof Collection)) {
			return true;
		}
		if (function != null) {
			Class<?> outputType = FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(((FunctionInvocationWrapper) function).getOutputType()));
			return Collection.class.isAssignableFrom(outputType);
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

	@SuppressWarnings({ "unchecked" })
	private <T> T getAndInstrumentFromContext(String name) {
		this.functionRegistration =
				new FunctionRegistration(context.getBean(name), name);

		Type type = FunctionContextUtils.
				findType(name, this.context.getBeanFactory());

		this.functionRegistration = functionRegistration.type(new FunctionType(type));

		((FunctionRegistry) this.catalog).register(functionRegistration);
		return this.catalog.lookup(name);
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


		if (this.catalog.size() >= 1 && this.catalog.size() <= 2) { // we may have RoutingFunction function
			String functionName = this.catalog.getNames(Function.class).stream()
					.filter(n -> !n.equals(RoutingFunction.FUNCTION_NAME))
					.findFirst().orElseGet(() -> null);
			if (functionName != null) {
				this.function = this.catalog.lookup(Function.class, functionName);
				return;
			}
			functionName = this.catalog.getNames(Supplier.class).stream()
					.findFirst().orElseGet(() -> null);
			if (functionName != null) {
				this.supplier = this.catalog.lookup(Supplier.class, functionName);
				return;
			}
			functionName = this.catalog.getNames(Consumer.class).stream()
					.findFirst().orElseGet(() -> null);
			if (functionName != null) {
				this.consumer = this.catalog.lookup(Consumer.class, functionName);
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
