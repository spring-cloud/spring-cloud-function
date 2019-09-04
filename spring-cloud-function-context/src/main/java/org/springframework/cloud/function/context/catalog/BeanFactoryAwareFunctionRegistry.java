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

import java.lang.reflect.Method;
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
import java.util.stream.Stream;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.config.FunctionContextUtils;
import org.springframework.cloud.function.context.config.RoutingFunction;
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
		implements FunctionRegistry, FunctionInspector, ApplicationContextAware {

	private static Log logger = LogFactory.getLog(BeanFactoryAwareFunctionRegistry.class);

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

	@Override
	public <T> T lookup(Class<?> type, String definition) {
		return this.lookup(definition, new String[] {});
	}

	@Override
	public int size() {
		return this.applicationContext.getBeanNamesForType(Supplier.class).length +
				this.applicationContext.getBeanNamesForType(Function.class).length +
				this.applicationContext.getBeanNamesForType(Consumer.class).length;
	}

	@SuppressWarnings("unchecked")
	public <T> T lookup(String definition, String... acceptedOutputTypes) {
		Object function = this.proxyInvokerIfNecessary((FunctionInvocationWrapper) this.compose(null, definition, acceptedOutputTypes));
		return (T) function;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Set<String> getNames(Class<?> type) {
		Set<String> registeredNames = registrationsByFunction.values().stream().flatMap(reg -> reg.getNames().stream())
				.collect(Collectors.toSet());
		if (type == null) {
			registeredNames.addAll(CollectionUtils.arrayToList(this.applicationContext.getBeanNamesForType(Function.class)));
			registeredNames.addAll(CollectionUtils.arrayToList(this.applicationContext.getBeanNamesForType(Supplier.class)));
			registeredNames.addAll(CollectionUtils.arrayToList(this.applicationContext.getBeanNamesForType(Consumer.class)));
		}
		else {
			registeredNames.addAll(CollectionUtils.arrayToList(this.applicationContext.getBeanNamesForType(type)));
		}
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

	public FunctionType getFunctionType(String name) {
		return FunctionType.of(FunctionTypeUtils.getFunctionType(this.lookup(name), this));
	}

	private Object locateFunction(String name) {
		Object function = null;
		if (this.applicationContext.containsBean(name)) {
			function = this.applicationContext.getBean(name);
		}
		if (function == null) {
			function = this.registrationsByName.get(name);
		}

		if (function != null && this.isKotlin(function.getClass())) {
			function = this.applicationContext.getBean("_" + name, FunctionRegistration.class);
		}
		return function;
	}

	private boolean isKotlin(Class<?> functionClass) {
		if (functionClass != null) {
			if ("kotlin.jvm.internal.Lambda".equals(functionClass.getName())) {
				return true;
			}
			else {
				return this.isKotlin(functionClass.getSuperclass());
			}
		}
		else {
			return false;
		}
	}


	private Type discoverFunctionType(Object function, String... names) {
		boolean beanDefinitionExists = false;
		for (int i = 0; i < names.length && !beanDefinitionExists; i++) {
			beanDefinitionExists = this.applicationContext.getBeanFactory().containsBeanDefinition(names[i]);
		}
		if (!beanDefinitionExists) {
			logger.info("BeanDefinition for function name(s) `" + Arrays.asList(names) +
					"` can not be located. FunctionType will be based on " + function.getClass());
		}
		return beanDefinitionExists
				? FunctionType.of(FunctionContextUtils.findType(applicationContext.getBeanFactory(), names)).getType()
						: new FunctionType(function.getClass()).getType();
	}

	private String discoverDefaultDefinitionIfNecessary(String definition) {
		if (StringUtils.isEmpty(definition)) {
			// the underscores are for Kotlin function registrations (see KotlinLambdaToFunctionAutoConfiguration)
			String[] functionNames  = Stream.of(this.applicationContext.getBeanNamesForType(Function.class))
					.filter(n -> !n.startsWith("_") && !n.equals(RoutingFunction.FUNCTION_NAME)).toArray(String[]::new);
			String[] consumerNames  = Stream.of(this.applicationContext.getBeanNamesForType(Consumer.class))
					.filter(n -> !n.startsWith("_") && !n.equals(RoutingFunction.FUNCTION_NAME)).toArray(String[]::new);
			String[] supplierNames  = Stream.of(this.applicationContext.getBeanNamesForType(Supplier.class))
					.filter(n -> !n.startsWith("_") && !n.equals(RoutingFunction.FUNCTION_NAME)).toArray(String[]::new);
			/*
			 * we may need to add BiFunction and BiConsumer at some point
			 */
			List<String> names = Stream
					.concat(Stream.of(functionNames), Stream.concat(Stream.of(consumerNames), Stream.of(supplierNames))).collect(Collectors.toList());

			if (!ObjectUtils.isEmpty(names)) {
				Assert.isTrue(names.size() == 1, "Found more then one function in BeanFactory: " + names);
				definition = names.get(0);
			}
			else {
				if (this.registrationsByName.size() > 0) {
					Assert.isTrue(this.registrationsByName.size() == 1, "Found more then one function in local registry");
					definition = this.registrationsByName.keySet().iterator().next();
				}
			}
		}
		return definition;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Function<?, ?> compose(Class<?> type, String definition, String... acceptedOutputTypes) {
		if (logger.isInfoEnabled()) {
			logger.info("Looking up function '" + definition + "' with acceptedOutputTypes: " + Arrays.asList(acceptedOutputTypes));
		}
		definition = discoverDefaultDefinitionIfNecessary(definition);
		if (StringUtils.isEmpty(definition)) {
			return null;
		}
		Function<?, ?> resultFunction = null;
		if (this.registrationsByName.containsKey(definition)) {
			Object targetFunction =  this.registrationsByName.get(definition).getTarget();
			Type functionType = this.registrationsByName.get(definition).getType().getType();
			resultFunction = new FunctionInvocationWrapper(targetFunction, functionType, definition, acceptedOutputTypes);
		}
		else {
			String[] names = StringUtils.delimitedListToStringArray(definition.replaceAll(",", "|").trim(), "|");
			StringBuilder composedNameBuilder = new StringBuilder();
			String prefix = "";

			Type originFunctionType = null;
			for (String name : names) {
				Object function = this.locateFunction(name);
				if (function == null) {
					logger.warn("!!! Failed to discover function '" + definition + "' in function catalog. "
							+ "Function available in catalog are: "  + this.getNames(null));
					return null;
				}

				composedNameBuilder.append(prefix);
				composedNameBuilder.append(name);

				FunctionRegistration<Object> registration;
				Type currentFunctionType = null;

				if (function instanceof FunctionRegistration) {
					registration = (FunctionRegistration<Object>) function;
					currentFunctionType = currentFunctionType == null ? registration.getType().getType() : currentFunctionType;
					function = registration.getTarget();
				}
				else {
					if (isFunctionPojo(function)) {
						Method functionalMethod = FunctionTypeUtils.discoverFunctionalMethod(function.getClass());
						currentFunctionType = FunctionTypeUtils.fromFunctionMethod(functionalMethod);
						function = this.proxyTarget(function, functionalMethod);
					}
					String[] aliasNames = this.getAliases(name).toArray(new String[] {});
					currentFunctionType = currentFunctionType == null ? this.discoverFunctionType(function, aliasNames) : currentFunctionType;
					registration = new FunctionRegistration<>(function, name).type(currentFunctionType);
				}

				registrationsByFunction.putIfAbsent(function, registration);
				registrationsByName.putIfAbsent(name, registration);
				function = new FunctionInvocationWrapper(function, currentFunctionType, name, acceptedOutputTypes);

				if (originFunctionType == null) {
					originFunctionType = currentFunctionType;
				}

				// composition
				if (resultFunction == null) {
					resultFunction = (Function<?, ?>) function;
				}
				else {
					originFunctionType = FunctionTypeUtils.compose(originFunctionType, currentFunctionType);
					resultFunction = new FunctionInvocationWrapper(resultFunction.andThen((Function) function),
							originFunctionType, composedNameBuilder.toString(), acceptedOutputTypes);
				}
				prefix = "|";
			}
			FunctionRegistration<Object> registration = new FunctionRegistration<Object>(resultFunction, definition)
					.type(originFunctionType);
			registrationsByFunction.putIfAbsent(resultFunction, registration);
			registrationsByName.putIfAbsent(definition, registration);
		}
		return resultFunction;
	}

	private boolean isFunctionPojo(Object function) {
		return !function.getClass().isSynthetic()
				&& !(function instanceof Supplier) && !(function instanceof Function) && !(function instanceof Consumer)
				&& !function.getClass().getPackage().getName().startsWith("org.springframework.cloud.function.compiler");
	}

	/*
	 * == OUTER PROXY ===
	 * For cases where function is POJO we need to be able to look it up as Function
	 * as well as the type of actual pojo (e.g., MyFunction f1 = catalog.lookup("myFunction");)
	 * To do this we wrap the target into CglibProxy (for cases when function is a POJO ) with the
	 * actual target class (e.g., MyFunction). Meanwhile the invocation will be delegated to
	 * the FunctionInvocationWrapper which will trigger the INNER PROXY. This effectively ensures that
	 * conversion, composition and/or fluxification would happen (code inside of FunctionInvocationWrapper)
	 * while the inner proxy invocation will delegate the invocation with already converted arguments
	 * to the actual target class (e.g., MyFunction).
	 */
	private Object proxyInvokerIfNecessary(FunctionInvocationWrapper functionInvoker) {
		if (functionInvoker != null && AopUtils.isCglibProxy(functionInvoker.getTarget())) {
			if (logger.isInfoEnabled()) {
				logger.info("Proxying POJO function: " + functionInvoker.functionDefinition + ". . ." + functionInvoker.target.getClass());
			}
			ProxyFactory pf = new ProxyFactory(functionInvoker.getTarget());
			pf.setProxyTargetClass(true);
			pf.setInterfaces(Function.class, Supplier.class, Consumer.class);
			pf.addAdvice(new MethodInterceptor() {
				@Override
				public Object invoke(MethodInvocation invocation) throws Throwable {
					// this will trigger the INNER PROXY
					if (ObjectUtils.isEmpty(invocation.getArguments())) {
						Object o =  functionInvoker.get();
						return o;
					}
					else {
						// this is where we probably would need to gather all arguments into tuples
						return functionInvoker.apply(invocation.getArguments()[0]);
					}

				}
			});
			return pf.getProxy();
		}
		return functionInvoker;
	}

	/*
	 * == INNER PROXY ===
	 * When dealing with POJO functions we still want to be able to treat them as any other
	 * function for purposes of composition, type conversion and fluxification.
	 * So this proxy will ensure that the target class can be represented as Function while delegating
	 * any call to apply to the actual target method.
	 * Since this proxy is part of the FunctionInvocationWrapper composition and copnversion will be applied
	 * as tyo any other function.
	 */
	private Object proxyTarget(Object targetFunction, Method actualMethodToCall) {
		ProxyFactory pf = new ProxyFactory(targetFunction);
		pf.setProxyTargetClass(true);
		pf.setInterfaces(Function.class);
		pf.addAdvice(new MethodInterceptor() {
			@Override
			public Object invoke(MethodInvocation invocation) throws Throwable {
				return actualMethodToCall.invoke(invocation.getThis(), invocation.getArguments());
			}
		});
		return pf.getProxy();
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

		private final boolean composed;

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
			Object input = FunctionTypeUtils.isMono(this.functionType)
					? Mono.empty()
							: (FunctionTypeUtils.isMono(this.functionType) ? Flux.empty() : null);

			return this.doApply(input, false);
		}

		public boolean isConsumer() {
			return FunctionTypeUtils.isConsumer(this.functionType);
		}

		public boolean isSupplier() {
			return FunctionTypeUtils.isSupplier(this.functionType);
		}

		public Object getTarget() {
			return target;
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		private Object invokeFunction(Object input) {
			Object invocationResult = null;
			if (this.target instanceof Function) {
				invocationResult = ((Function) target).apply(input);
			}
			else if (this.target instanceof Supplier) {
				invocationResult = ((Supplier) target).get();
			}
			else {
				((Consumer) this.target).accept(input);
			}

			if (!(this.target instanceof Consumer) && logger.isDebugEnabled()) {
				logger.debug("Result of invocation of \"" + this.functionDefinition + "\" function is '" + invocationResult + "'");
			}
			return invocationResult;
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private Object doApply(Object input, boolean consumer) {
			if (logger.isDebugEnabled()) {
				logger.debug("Applying function: " + this.functionDefinition);
			}

			Object result;
			if (input instanceof Publisher) {
				input = this.composed ? input :
					this.convertInputPublisherIfNecessary((Publisher<?>) input, FunctionTypeUtils.getInputType(this.functionType, 0));
				if (FunctionTypeUtils.isReactive(FunctionTypeUtils.getInputType(this.functionType, 0))) {
					result = this.invokeFunction(input);
					result = result == null ? Mono.empty() : result;
				}
				else {
					if (this.composed) {
						return input instanceof Mono
								? Mono.from((Publisher<?>) input).transform((Function) this.target)
										: Flux.from((Publisher<?>) input).transform((Function) this.target);
					}
					else {
						if (FunctionTypeUtils.isConsumer(functionType)) {
							result = input instanceof Mono
									? Mono.from((Publisher) input).doOnNext((Consumer) this.target).then()
											: Flux.from((Publisher) input).doOnNext((Consumer) this.target).then();
						}
						else {
							result = input instanceof Mono
									? Mono.from((Publisher) input).map(value -> this.invokeFunction(value))
											: Flux.from((Publisher) input).map(value -> this.invokeFunction(value));
						}
					}
				}
			}
			else {
				Type type = FunctionTypeUtils.getInputType(this.functionType, 0);
				if (!this.composed && !FunctionTypeUtils.isMultipleInputArguments(this.functionType) && FunctionTypeUtils.isReactive(type)) {
					Publisher<?> publisher = FunctionTypeUtils.isFlux(type)
							? input == null ? Flux.empty() : Flux.just(input)
									: input == null ? Mono.empty() : Mono.just(input);
					if (logger.isDebugEnabled()) {
						logger.debug("Invoking reactive function '" + this.functionType + "' with non-reactive input "
								+ "should at least assume reactive output (e.g., Function<String, Flux<String>> f3 = catalog.lookup(\"echoFlux\");), "
								+ "otherwise invocation will result in ClassCastException.");
					}
					result = this.invokeFunction(this.convertInputPublisherIfNecessary(publisher, FunctionTypeUtils.getInputType(this.functionType, 0)));
				}
				else {
					result = this.invokeFunction(this.composed ? input
							: this.convertInputValueIfNecessary(input, FunctionTypeUtils.getInputType(this.functionType, 0)));
				}
			}

			// Outputs will be converted only if we're told how (via  acceptedOutputMimeTypes), otherwise output returned as is.
			if (!ObjectUtils.isEmpty(this.acceptedOutputMimeTypes)) {
				result = result instanceof Publisher
						? this.convertOutputPublisherIfNecessary((Publisher<?>) result, this.acceptedOutputMimeTypes)
								: this.convertOutputValueIfNecessary(result, this.acceptedOutputMimeTypes);
			}

			return result;
		}

		private Object convertOutputValueIfNecessary(Object value, String... acceptedOutputMimeTypes) {
			logger.debug("Applying type conversion on output value");
			Object convertedValue = null;
			if (FunctionTypeUtils.isMultipleArgumentsHolder(value)) {
				int outputCount = FunctionTypeUtils.getOutputCount(this.functionType);
				Object[] convertedInputArray = new Object[outputCount];
				for (int i = 0; i < outputCount; i++) {
					Expression parsed = new SpelExpressionParser().parseExpression("getT" + (i + 1) + "()");
					Object outputArgument = parsed.getValue(value);
					try {
						convertedInputArray[i] = outputArgument instanceof Publisher
								? this.convertOutputPublisherIfNecessary((Publisher<?>) outputArgument, acceptedOutputMimeTypes[i])
										: this.convertOutputValueIfNecessary(outputArgument, acceptedOutputMimeTypes);
					}
					catch (ArrayIndexOutOfBoundsException e) {
						throw new IllegalStateException("The number of 'acceptedOutputMimeTypes' for function '" + this.functionDefinition
								+ "' is (" + acceptedOutputMimeTypes.length
								+ "), which does not match the number of actual outputs of this function which is (" +  outputCount + ").", e);
					}

				}
				convertedValue = Tuples.fromArray(convertedInputArray);
			}
			else if (value != null) {
				List<MimeType> acceptedContentTypes = MimeTypeUtils.parseMimeTypes(acceptedOutputMimeTypes[0].toString());

				convertedValue = acceptedContentTypes.stream()
						.map(acceptedContentType -> messageConverter
								.toMessage(value, new MessageHeaders(Collections.singletonMap(MessageHeaders.CONTENT_TYPE, acceptedContentType))))
						.filter(v -> v != null)
						.findFirst().orElse(null);
			}
			return convertedValue;

		}

		private Publisher<?> convertOutputPublisherIfNecessary(Publisher<?> publisher, String... acceptedOutputMimeTypes) {
			if (logger.isDebugEnabled()) {
				logger.debug("Applying type conversion on output Publisher " + publisher);
			}

			Publisher<?> result = publisher instanceof Mono
					? Mono.from(publisher) .map(value -> this.convertOutputValueIfNecessary(value, acceptedOutputMimeTypes))
							: Flux.from(publisher).map(value -> this.convertOutputValueIfNecessary(value, acceptedOutputMimeTypes));
			return result;
		}

		private Publisher<?> convertInputPublisherIfNecessary(Publisher<?> publisher, Type type) {
			if (logger.isDebugEnabled()) {
				logger.debug("Applying type conversion on input Publisher " + publisher);
			}

			Publisher<?> result = publisher instanceof Mono
					? Mono.from(publisher).map(value -> this.convertInputValueIfNecessary(value, type))
							: Flux.from(publisher).map(value -> this.convertInputValueIfNecessary(value, type));
			return result;
		}

		private Object convertInputValueIfNecessary(Object value, Type type) {
			if (logger.isDebugEnabled()) {
				logger.debug("Applying type conversion on input value " + value);
				logger.debug("Function type: " + this.functionType);
			}

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
				if (logger.isDebugEnabled()) {
					logger.debug("Raw type of value: " + value + "is " + rawType);
				}

				if (rawType instanceof ParameterizedType) {
					rawType = ((ParameterizedType) rawType).getRawType();
				}
				if (value instanceof Message<?>) { // see AWS adapter with Optional payload
					if (messageNeedsConversion(rawType, (Message<?>) value)) {
						convertedValue = FunctionTypeUtils.isTypeCollection(type)
								? messageConverter.fromMessage((Message<?>) value, (Class<?>) rawType, type)
										:  messageConverter.fromMessage((Message<?>) value, (Class<?>) rawType);
						if (logger.isDebugEnabled()) {
							logger.debug("Converted from Message: " + convertedValue);
						}
						if (FunctionTypeUtils.isMessage(type)) {
							convertedValue = MessageBuilder.withPayload(convertedValue).copyHeaders(((Message<?>) value).getHeaders()).build();
						}
					}
					else if (!FunctionTypeUtils.isMessage(type)) {
						convertedValue = ((Message<?>) convertedValue).getPayload();
					}
				}
				else if (rawType instanceof Class<?>) { // see AWS adapter with WildardTypeImpl and Azure with Voids
					convertedValue = conversionService.convert(value, (Class<?>) rawType);
				}
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Converted input value " + convertedValue);
			}
			return convertedValue;
		}

		private boolean messageNeedsConversion(Type rawType, Message<?> message) {
			return rawType instanceof Class<?>
				&& !(message.getPayload() instanceof Optional)
				&& !(message.getPayload().getClass().isAssignableFrom(((Class<?>) rawType)));
		}
	}
}
