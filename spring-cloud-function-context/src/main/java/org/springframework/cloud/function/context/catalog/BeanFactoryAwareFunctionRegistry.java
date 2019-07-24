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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import reactor.util.function.Tuples;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.cloud.function.context.AbstractSpringFunctionAdapterInitializer;
import org.springframework.cloud.function.context.FunctionCatalog;
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
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;


/**
 * Implementation of {@link FunctionRegistry} and {@link FunctionCatalog} which is aware of the
 * underlying {@link BeanFactory} to access available functions. Functions that are registered via
 * {@link #register(FunctionRegistration)} operation are stored/cached locally.
 *
 * @author Oleg Zhurakousky
 * @since 3.0
 */
public class BeanFactoryAwareFunctionRegistry
		implements FunctionRegistry, FunctionInspector, ApplicationContextAware, SmartInitializingSingleton {

	private static Log logger = LogFactory.getLog(AbstractSpringFunctionAdapterInitializer.class);

	private ConfigurableApplicationContext applicationContext;

	private final Map<Object, FunctionRegistration<Object>> registrationsByFunction = new HashMap<>();

	private final Map<String, FunctionRegistration<Object>> registrationsByName = new HashMap<>();

	private final ConversionService conversionService;

	private final CompositeMessageConverter messageConverter;

	public BeanFactoryAwareFunctionRegistry(ConversionService conversionService,
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
	public <T> T lookup(String definition, String... acceptedOutputTypes) {
		Assert.notEmpty(acceptedOutputTypes, "'acceptedOutputTypes' must not be null or empty");
		return (T) this.compose(null, definition, acceptedOutputTypes);
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
		FunctionRegistration<?> registration = this.registrationsByFunction.get(function);
		// need to do this due to the deployer not wrapping the actual target into FunctionInvocationWrapper
		// hence the lookup would need to be made by the actual target
		if (registration == null && function instanceof FunctionInvocationWrapper) {
			function = ((FunctionInvocationWrapper) function).target;
		}
		return this.registrationsByFunction.get(function);
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
	private Function<?, ?> compose(Class<?> type, String definition, String... acceptedOutputTypes) {
		Function<?, ?> resultFunction = null;
		if (this.registrationsByName.containsKey(definition)) {
			Object targetFunction =  this.registrationsByName.get(definition).getTarget();
			Type functionType = this.registrationsByName.get(definition).getType().getType();
			resultFunction = new FunctionInvocationWrapper(targetFunction, functionType, definition, acceptedOutputTypes);
		}
		else {
			if (StringUtils.isEmpty(definition)) {
				String[] functionNames = this.applicationContext.getBeanNamesForType(Function.class);
				Assert.notEmpty(functionNames, "Can't find any functions in BeanFactory");
				Assert.isTrue(functionNames.length == 1, "Found more then one function in BeanFactory");
				definition = functionNames[0];
			}
			String[] names = StringUtils.delimitedListToStringArray(definition.replaceAll(",", "|").trim(), "|");
			StringBuilder composedNameBuilder = new StringBuilder();
			String prefix = "";
			Type composedFunctionType = null;
			for (String name : names) {
				Object function = this.locateFunction(name);
				if (function == null) {
					return null;
				}
				if (composedFunctionType == null) {
					composedFunctionType = beanDefinitionExists(name)
							? FunctionType.of(FunctionContextUtils.findType(
									(ConfigurableListableBeanFactory) applicationContext.getBeanFactory(), name)).getType()
							: new FunctionType(function.getClass()).getType();
				}
				composedNameBuilder.append(prefix);
				composedNameBuilder.append(name);
				FunctionRegistration<Object> registration;
				Type functionType = null;
				if (function instanceof FunctionRegistration) {
					registration = (FunctionRegistration<Object>) function;
					functionType = registration.getType().getType();
					function = registration.getTarget();
				}
				else {
					String[] aliasNames = this.getAliases(name).toArray(new String[] {});
					functionType = beanDefinitionExists(aliasNames)
							? FunctionType.of(FunctionContextUtils.findType(
									(ConfigurableListableBeanFactory) applicationContext.getBeanFactory(), aliasNames)).getType()
							: new FunctionType(function.getClass()).getType();
					registration = new FunctionRegistration<>(function, name).type(functionType);
				}

				registrationsByFunction.putIfAbsent(function, registration);
				registrationsByName.putIfAbsent(name, registration);
				function = new FunctionInvocationWrapper(function, functionType, composedNameBuilder.toString(), acceptedOutputTypes);
				if (resultFunction == null) {
					resultFunction = (Function<?, ?>) function;
				}
				else {
					composedFunctionType = FunctionTypeUtils.compose(composedFunctionType, functionType);
					resultFunction = new FunctionInvocationWrapper(resultFunction.andThen((Function) function),
							composedFunctionType, composedNameBuilder.toString(), acceptedOutputTypes);
					registration = new FunctionRegistration<Object>(resultFunction, composedNameBuilder.toString())
							.type(composedFunctionType);
					registrationsByFunction.putIfAbsent(resultFunction, registration);
					registrationsByName.putIfAbsent(composedNameBuilder.toString(), registration);
				}
				prefix = "|";
			}

		}
		return resultFunction;
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

		private final Type functionType;

		private boolean composed;

		private final String[] acceptedOutputMimeTypes;

		private final String functionDefinition;

		FunctionInvocationWrapper(Object target, Type functionType, String functionDefinition, String... acceptedOutputMimeTypes) {
			this.target = target;

			this.composed = !target.getClass().getName().contains("EnhancerBySpringCGLIB") && target.getClass().getDeclaredFields().length > 1;
			this.functionType = functionType;
			this.acceptedOutputMimeTypes = acceptedOutputMimeTypes;
			this.functionDefinition = functionDefinition;
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
			Object input = FunctionTypeUtils.isMono(functionType)
					? Mono.empty()
							: (FunctionTypeUtils.isMono(functionType) ? Flux.empty() : null);

			return this.doApply(input, false);
		}

		public boolean isConsumer() {
			return this.target instanceof Consumer;
		}

		public boolean isFunction() {
			return this.target instanceof Function;
		}

		public boolean isSupplier() {
			return this.target instanceof Supplier;
		}

		public Object getTarget() {
			return target;
		}

//		public boolean isMultipleOutput() {
//
//			Type type = FunctionTypeUtils.getInputType(functionType, 0);
//			return FunctionTypeUtils.isFlux(type);
//		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		private Object invokeFunction(Object input) {
			if (target instanceof FunctionInvocationWrapper || target instanceof Function) {
				return ((Function) target).apply(input);
			}
			else if (target instanceof Supplier) {
				return ((Supplier) target).get();
			}
			else {
				((Consumer) target).accept(input);
				return null;
			}
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private Object doApply(Object input, boolean consumer) {
			if (logger.isDebugEnabled()) {
				logger.debug("Applying function: " + this.functionDefinition);
			}

			Object result = null;
			if (input instanceof Publisher) {
				input = this.composed ? input :
					this.convertInputPublisherIfNecessary((Publisher<?>) input, FunctionTypeUtils.getInputType(functionType, 0));
				if (FunctionTypeUtils.isReactive(FunctionTypeUtils.getInputType(functionType, 0))) {
					result = this.invokeFunction(input);
					if (result == null) {
						result = Mono.empty();
					}
				}
				else {
					if (this.composed) {
						return input instanceof Mono
								? Mono.from((Publisher<?>) input).transform((Function) target)
										: Flux.from((Publisher<?>) input).transform((Function) target);
					}
					else {
						boolean isConsumer = FunctionTypeUtils.isConsumer(functionType);
						Publisher res;
						if (isConsumer) {
							res = input instanceof Mono ? Mono.from((Publisher) input).doOnNext((Consumer) this.target).then()
									: Flux.from((Publisher) input).doOnNext((Consumer) this.target).then();
						}
						else {
							res = input instanceof Mono
									? Mono.from((Publisher) input).map(value -> this.invokeFunction(value))
											: Flux.from((Publisher) input).map(value -> this.invokeFunction(value));
						}
						return res;
					}
				}
			}
			else {
				Type type = FunctionTypeUtils.getInputType(functionType, 0);
				if (!composed && !FunctionTypeUtils.isMultipleInputArguments(functionType) && FunctionTypeUtils.isReactive(type)) {
					Publisher<?> publisher = FunctionTypeUtils.isFlux(type)
							? input == null ? Flux.empty() : Flux.just(input)
									: input == null ? Mono.empty() : Mono.just(input);
					publisher = this.convertInputPublisherIfNecessary(publisher, FunctionTypeUtils.getInputType(functionType, 0));
					result = this.invokeFunction(publisher);
				}
				else {
					result = this.invokeFunction(this.composed ? input
							: this.convertInputValueIfNecessary(input, FunctionTypeUtils.getInputType(functionType, 0)));
				}
			}

			if (!ObjectUtils.isEmpty(acceptedOutputMimeTypes)) {
				if (result instanceof Publisher) {
					result = this.convertOutputPublisherIfNecessary((Publisher<?>) result, this.acceptedOutputMimeTypes);
				}
				else {
					result = this.convertOutputValueIfNecessary(result, this.acceptedOutputMimeTypes);
				}
			}

			if (!(result instanceof Publisher) && (!(target instanceof FunctionInvocationWrapper) && target instanceof Supplier)) {
				/*
				 * This is ONLY relevant for web, so consider exposing some property or may be
				 * the fact that this is a rare case (Supplier) leave it temporarily as is.
				 */
				return Flux.just(result);
			}

			return result;
		}

		private Object convertOutputValueIfNecessary(Object value, String... acceptedOutputMimeTypes) {
			logger.info("Converting output value ");
			Object convertedValue = null;
			if (FunctionTypeUtils.isMultipleArgumentsHolder(value)) {
				int outputCount = FunctionTypeUtils.getOutputCount(functionType);
				Object[] convertedInputArray = new Object[outputCount];
				for (int i = 0; i < outputCount; i++) {
					Expression parsed = new SpelExpressionParser().parseExpression("getT" + (i + 1) + "()");
					Object outputArgument = parsed.getValue(value);
					if (outputArgument instanceof Publisher) {
						outputArgument = this.convertOutputPublisherIfNecessary((Publisher<?>) outputArgument, acceptedOutputMimeTypes[i]);
					}
					else {
						outputArgument = this.convertOutputValueIfNecessary(outputArgument, acceptedOutputMimeTypes);
					}
					convertedInputArray[i] = outputArgument;
				}
				convertedValue = Tuples.fromArray(convertedInputArray);
			}
			else {
				List<MimeType> acceptedContentTypes = MimeTypeUtils.parseMimeTypes(acceptedOutputMimeTypes[0].toString());
				for (MimeType acceptedContentType : acceptedContentTypes) {
					try {
						MessageHeaders headers = new MessageHeaders(Collections.singletonMap(MessageHeaders.CONTENT_TYPE, acceptedContentType));
						convertedValue = messageConverter.toMessage(value, headers);
						if (convertedValue != null) {
							break;
						}
					}
					catch (Exception e) {
						// ignore
					}
				}

			}
			return convertedValue;

		}

		private Publisher<?> convertOutputPublisherIfNecessary(Publisher<?> publisher, String... acceptedOutputMimeTypes) {
			System.out.println("Converting output publisher");
			Publisher<?> result = publisher instanceof Mono
					? Mono.from(publisher).map(value -> this.convertOutputValueIfNecessary(value, acceptedOutputMimeTypes))
							: Flux.from(publisher).map(value -> this.convertOutputValueIfNecessary(value, acceptedOutputMimeTypes));
			return result;
		}

		private Publisher<?> convertInputPublisherIfNecessary(Publisher<?> publisher, Type type) {
			System.out.println("Converting publisher");
			Publisher<?> result = publisher instanceof Mono
					? Mono.from(publisher).map(value -> this.convertInputValueIfNecessary(value, type))
							: Flux.from(publisher).map(value -> this.convertInputValueIfNecessary(value, type));
			return result;
		}

		private Object convertInputValueIfNecessary(Object value, Type type) {
			System.out.println("Converting value");
			Object convertedValue = value;
			if (FunctionTypeUtils.isMultipleArgumentsHolder(value)) {
				int inputCount = FunctionTypeUtils.getInputCount(functionType);
				Object[] convertedInputArray = new Object[inputCount];
				for (int i = 0; i < inputCount; i++) {
					Expression parsed = new SpelExpressionParser().parseExpression("getT" + (i + 1) + "()");
					Object inptArgument = parsed.getValue(value);
					inptArgument = inptArgument instanceof Publisher
							? this.convertInputPublisherIfNecessary((Publisher<?>) inptArgument, FunctionTypeUtils.getInputType(functionType, i))
									: this.convertInputValueIfNecessary(inptArgument, FunctionTypeUtils.getInputType(functionType, i));
					convertedInputArray[i] = inptArgument;
				}
				convertedValue = Tuples.fromArray(convertedInputArray);
			}
			else {
				// this needs revisiting as the type is not always Class (think really complex types)
				Type rawType =  FunctionTypeUtils.unwrapActualTypeByIndex(type, 0);
				if (!(rawType instanceof Class<?>) && rawType instanceof ParameterizedType) {
					rawType = ((ParameterizedType) rawType).getRawType();
				}
				if (value instanceof Message<?>) { // see AWS adapter with Optional payload
					if (!(((Message<?>) value).getPayload() instanceof Optional)) {
						convertedValue = messageConverter.fromMessage((Message<?>) value, (Class<?>) rawType, type);
						if (FunctionTypeUtils.isMessage(type)) {
							convertedValue = MessageBuilder.withPayload(convertedValue).copyHeaders(((Message<?>) value).getHeaders()).build();
						}
					}
				}
				else {
					if (rawType instanceof Class<?>) { // see AWS adapter with WildardTypeImpl and Azure with Voids
						convertedValue = conversionService.convert(value, (Class<?>) rawType);
					}
				}
			}
			return convertedValue;
		}

	}
}
