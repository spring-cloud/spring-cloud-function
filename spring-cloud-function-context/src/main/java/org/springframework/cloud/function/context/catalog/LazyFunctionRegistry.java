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

package org.springframework.cloud.function.context.catalog;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.cloud.function.context.AbstractSpringFunctionAdapterInitializer;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.config.FunctionContextUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.lang.Nullable;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class LazyFunctionRegistry
		implements FunctionRegistry, FunctionInspector, ApplicationContextAware, SmartInitializingSingleton {

	private static Log logger = LogFactory.getLog(AbstractSpringFunctionAdapterInitializer.class);

	private ConfigurableApplicationContext applicationContext;

	private Map<Object, FunctionRegistration<Object>> registrationsByFunction = new HashMap<>();

	private Map<String, FunctionRegistration<Object>> registrationsByName = new HashMap<>();

	private final ConversionService conversionService;

	private final CompositeMessageConverter messageConverter;

	public LazyFunctionRegistry(ConversionService conversionService,
			@Nullable CompositeMessageConverter messageConverter) {
		this.conversionService = conversionService;
		this.messageConverter = messageConverter;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T lookup(Class<?> type, String definition) {
		return (T) this.compose(type, definition);
	}

	@Override
	public int size() {
		return this.applicationContext.getBeanNamesForType(Supplier.class).length +
				this.applicationContext.getBeanNamesForType(Function.class).length +
				this.applicationContext.getBeanNamesForType(Consumer.class).length;
	}

	@SuppressWarnings("unchecked")
	public <T> T lookup(String definition, MimeType... acceptedOutputTypes) {
		Assert.notEmpty(acceptedOutputTypes, "'acceptedOutputTypes' must not be null or empty");
		return (T) this.compose(null, definition, acceptedOutputTypes);
	}

	@Override
	public boolean isMessage(Object function) {
		if (function instanceof FunctionInvocationWrapper) {
			function = ((FunctionInvocationWrapper) function).target;
		}
		return FunctionInspector.super.isMessage(function);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void afterSingletonsInstantiated() {
		Map<String, FunctionRegistration> beansOfType = this.applicationContext
				.getBeansOfType(FunctionRegistration.class);
		for (FunctionRegistration fr : beansOfType.values()) {
			this.registrationsByFunction.putIfAbsent(fr.getTarget(), fr);
			for (Object name : fr.getNames()) {
				this.registrationsByName.putIfAbsent((String) name, fr);
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public Set<String> getNames(Class<?> type) {
		Set<String> registeredNames = registrationsByFunction.values().stream().flatMap(reg -> reg.getNames().stream())
				.collect(Collectors.toSet());
		registeredNames.addAll(CollectionUtils.arrayToList(this.applicationContext.getBeanNamesForType(type)));
		return registeredNames;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> void register(FunctionRegistration<T> registration) {
		this.registrationsByFunction.put(registration.getTarget(), (FunctionRegistration<Object>) registration);
		for (String name : registration.getNames()) {
			this.registrationsByName.put(name, (FunctionRegistration<Object>) registration);
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = (ConfigurableApplicationContext) applicationContext;
	}

	@Override
	public FunctionRegistration<?> getRegistration(Object function) {
		if (function instanceof FunctionInvocationWrapper) {
			function = ((FunctionInvocationWrapper) function).target;
		}
		return this.registrationsByFunction.get(function);
	}

	private Collection<String> getAliases(String key) {
		Collection<String> names = new LinkedHashSet<>();
		String value = getQualifier(key);
		if (value.equals(key) && this.applicationContext != null) {
			names.addAll(Arrays.asList(this.applicationContext.getBeanFactory().getAliases(key)));
		}
		names.add(value);
		return names;
	}

	private String getQualifier(String key) {
		if (this.applicationContext != null && this.applicationContext.getBeanFactory().containsBeanDefinition(key)) {
			BeanDefinition beanDefinition = this.applicationContext.getBeanFactory().getBeanDefinition(key);
			Object source = beanDefinition.getSource();
			if (source instanceof StandardMethodMetadata) {
				StandardMethodMetadata metadata = (StandardMethodMetadata) source;
				Qualifier qualifier = AnnotatedElementUtils.findMergedAnnotation(metadata.getIntrospectedMethod(),
						Qualifier.class);
				if (qualifier != null && qualifier.value().length() > 0) {
					return qualifier.value();
				}
			}
		}
		return key;
	}

	private Object locateFunction(String name) {
		Object function = null;
		if (this.applicationContext.containsBean(name)) {
			function = this.applicationContext.getBean(name);
		}
		if (function == null) {
			function = this.registrationsByName.get(name);
		}
		return function;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Function<?, ?> compose(Class<?> type, String definition, MimeType... acceptedOutputTypes) {
		Function<?, ?> resultFunction = null;
		if (this.registrationsByName.containsKey(definition)) {
			resultFunction = new FunctionInvocationWrapper(this.registrationsByName.get(definition), false,
					acceptedOutputTypes);
		}
		else {
			if (StringUtils.isEmpty(definition)) {
				String[] functionNames = this.applicationContext.getBeanNamesForType(Function.class);
				Assert.notEmpty(functionNames, "Can't find any functions in BeanFactory");
				Assert.isTrue(functionNames.length == 1, "Found more then one function in BeanFactory");
				definition = functionNames[0];
			}
			String[] names = StringUtils.delimitedListToStringArray(definition.replaceAll(",", "|").trim(), "|");

			FunctionType previousFunctionType = null;

			StringBuilder composedNameBuilder = new StringBuilder();
			String prefix = "";
			for (String name : names) {
				Object function = this.locateFunction(name);
				if (function == null) {
					return null;
				}
				composedNameBuilder.append(prefix);
				composedNameBuilder.append(name);

				FunctionRegistration<Object> registration;
				FunctionType funcType;
				if (function instanceof FunctionRegistration) {
					registration = (FunctionRegistration<Object>) function;
					funcType = registration.getType();
					function = registration.getTarget();
				}
				else {
					String[] aliasNames = this.getAliases(name).toArray(new String[] {});
					funcType = beanDefinitionExists(aliasNames)
							? FunctionType.of(FunctionContextUtils.findType(
									(ConfigurableListableBeanFactory) applicationContext.getBeanFactory(), aliasNames))
							: new FunctionType(function.getClass());
					registration = new FunctionRegistration<>(function, name).type(funcType);
				}

				registrationsByFunction.putIfAbsent(function, registration);
				registrationsByName.putIfAbsent(name, registration);
				function = new FunctionInvocationWrapper(registration, false, acceptedOutputTypes);
				if (resultFunction == null) {
					resultFunction = (Function<?, ?>) function;
				}
				else {
					resultFunction = resultFunction.andThen((Function) function);
					if (this.getOutputWrapper(function).isAssignableFrom(Flux.class)) {
						funcType = FunctionType.compose(previousFunctionType.wrap(Flux.class), funcType);
						logger.info("Since composed function " + composedNameBuilder.toString()
								+ " consists of at least one function "
								+ "with return type Publisher, its resulting signature is Function<?, Publisher<?>>");
					}
					else if (this.getOutputWrapper(function).isAssignableFrom(Mono.class)) {
						funcType = FunctionType.compose(previousFunctionType.wrap(Mono.class), funcType);
					}
					else {
						funcType = FunctionType.compose(previousFunctionType, funcType);
					}

					registration = new FunctionRegistration<Object>(resultFunction, composedNameBuilder.toString())
							.type(funcType);
					registrationsByFunction.putIfAbsent(resultFunction, registration);
					registrationsByName.putIfAbsent(composedNameBuilder.toString(), registration);
					resultFunction = new FunctionInvocationWrapper(registration, true, acceptedOutputTypes);
				}
				previousFunctionType = funcType;
				prefix = "|";
			}
		}

		return resultFunction;
	}

	private boolean beanDefinitionExists(String... names) {
		for (String name : names) {
			if (this.applicationContext.getBeanFactory().containsBeanDefinition(name)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Single wrapper for all Suppliers, Functions and Consumers managed by this
	 * catalog.
	 *
	 * @author Oleg Zhurakousky
	 *
	 */
	public class FunctionInvocationWrapper implements Function<Object, Object>, Consumer<Object>, Supplier<Object> {

		private final Object target;

		private final FunctionRegistration<?> functionRegistration;

		private final boolean composed;

		private final FunctionTypeConversionHelper functionTypeConversionHelper;

		private final MimeType[] acceptedOutputTypes;

		FunctionInvocationWrapper(FunctionRegistration<?> functionRegistration, boolean composed,
				MimeType... acceptedOutputTypes) {
			this.target = functionRegistration.getTarget();
			this.functionRegistration = functionRegistration;
			this.composed = composed;
			this.acceptedOutputTypes = acceptedOutputTypes;
			this.functionTypeConversionHelper = new FunctionTypeConversionHelper(this.functionRegistration,
					conversionService, messageConverter);
		}

		@Override
		public void accept(Object input) {
			this.doApply(input, true);
		}

		@Override
		public Object apply(Object input) {
			return this.doApply(input, false);
		}

		@Override
		public Object get() {
			// wrap/unwrap to/from reactive
			Object input = Mono.class.isAssignableFrom(this.functionRegistration.getType().getInputWrapper())
					? Mono.empty()
					: (Flux.class.isAssignableFrom(this.functionRegistration.getType().getInputWrapper()) ? Flux.empty()
							: null);

			return this.doApply(input, false);
		}

		public Object getTarget() {
			return this.target;
		}

		@SuppressWarnings("unchecked")
		private Object doApply(Object input, boolean consumer) {
			if (logger.isDebugEnabled()) {
				logger.debug("Applying function: " + this.functionRegistration.getNames());
			}

			if (input != null) {
				input = this.wrapInputToReactiveIfNecessary(input);
			}

			Object result;
			if (input instanceof Publisher) {
				if (input != null && !this.composed) {
					input = this.functionTypeConversionHelper.convertInputIfNecessary(input);
				}
				result = this.applyReactive((Publisher<Object>) input, consumer);
			}
			else {
				if (Publisher.class.isAssignableFrom(this.functionRegistration.getType().getInputWrapper())) {
					throw new IllegalArgumentException("Invoking reactive function as imperative is not "
							+ "allowed. Function name(s): " + this.functionRegistration.getNames());
				}
				else {
					if (input != null && !this.composed) {
						input = this.functionTypeConversionHelper.convertInputIfNecessary(input);
					}
					result = this.applyImperative(input, consumer);
				}
			}

			result = this.functionTypeConversionHelper.convertOutputIfNecessary(result, this.acceptedOutputTypes);

			if (!(result instanceof Publisher) && this.functionRegistration.getTarget() instanceof Supplier) {
				/*
				 * This is ONLY relevant for web, so consider exposing some property or may be
				 * the fact that this is a rare case (Supplier) leave it temporarily as is.
				 */

				return Flux.just(this.wrapOutputToReactiveIfNecessary(result));
			}
			else {
				return this.wrapOutputToReactiveIfNecessary(result);
			}

		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private Object wrapOutputToReactiveIfNecessary(Object result) {
			if (Flux.class.isAssignableFrom(this.functionRegistration.getType().getOutputWrapper())) {
				result = result instanceof Publisher ? Flux.from((Publisher) result) : Flux.just(result);
			}
			else if (Mono.class.isAssignableFrom(this.functionRegistration.getType().getOutputWrapper())) {
				result = result instanceof Publisher ? Mono.from((Publisher) result) : Mono.just(result);
			}
			return result;
		}

		/*
		 * For functions of type `Function<?, Publisher<?>>` the input will be converted
		 * to Publisher as well resulting in `Function<Publisher<?>, Publisher<?>>`
		 */
		private Object wrapInputToReactiveIfNecessary(Object input) {
			if (input != null && !(input instanceof Publisher)) { // for Function<Object, Publisher>
				if (Flux.class.isAssignableFrom(this.functionRegistration.getType().getInputWrapper())) {
					input = Flux.just(input);
				}
				else if (Mono.class.isAssignableFrom(this.functionRegistration.getType().getInputWrapper())) {
					input = Mono.just(input);
				}
			}
			return input;
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private Object applyImperative(Object input, boolean consumer) {
			Object result = null;
			if (this.target instanceof Function) {
				if (Flux.class.isAssignableFrom(this.functionRegistration.getType().getInputWrapper())) {
					result = ((Function) this.target).apply(Flux.just(input));
					// we may need to convert output as well
				}
				else {
					result = ((Function) this.target).apply(input);
				}
			}
			else if (this.target instanceof Consumer) {
				((Consumer) this.target).accept(input);
			}
			else if (this.target instanceof Supplier) {
				result = ((Supplier) this.target).get();
			}
			else {
				throw new UnsupportedOperationException(
						"Target of type " + this.target.getClass() + " is not supported");
			}
			return result;
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private Object applyReactive(Publisher<Object> publisher, boolean consumer) {
			Object result;
			if (this.target instanceof Function) {
				if (Publisher.class.isAssignableFrom(this.functionRegistration.getType().getInputWrapper())) {
					result = ((Function) this.target).apply(publisher);
				}
				else {
					if (Void.class.isAssignableFrom(this.functionRegistration.getType().getInputType()) && !functionRegistration.getType().isMessage()) {
						result = ((Function) this.target).apply(null);
						result = publisher instanceof Mono ? Mono.just(result) : Flux.just(result);
					}
					else {
						result = publisher instanceof Mono
								? Mono.from(publisher).map(value -> ((Function) this.target).apply(value))
								: Flux.from(publisher).map(value -> ((Function) this.target).apply(value));
					}
				}
			}
			else if (this.target instanceof Consumer) {
				if (Publisher.class.isAssignableFrom(this.functionRegistration.getType().getInputWrapper())) {
					((Consumer<Publisher<?>>) this.target).accept(publisher);
					result = null;
				}
				else {
					result = publisher instanceof Flux ? Flux.from(publisher).doOnNext((Consumer) this.target).then()
							: Mono.from(publisher).doOnNext((Consumer) this.target).then();
					if (consumer) {
						((Mono<?>) result).subscribe();
					}
				}
			}
			else if (this.target instanceof Supplier) {
				result = ((Supplier<?>) this.target).get();
			}
			else {
				throw new UnsupportedOperationException(
						"Target of type " + this.target.getClass() + " is not supported");
			}
			return result;
		}
	}
}
