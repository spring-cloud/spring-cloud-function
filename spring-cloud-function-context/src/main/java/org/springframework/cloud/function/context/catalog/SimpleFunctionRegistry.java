/*
 * Copyright 2020-2020 the original author or authors.
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

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.core.convert.ConversionService;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;


/**
 *
 * Basic implementation of FunctionRegistry which maintains the cache of registered functions while
 * decorating them with additional features such as transparent type conversion, composition, routing etc.
 *
 * Unlike {@link BeanFactoryAwareFunctionRegistry}, this implementation does not depend on {@link BeanFactory}.
 *
 * @author Oleg Zhurakousky
 *
 * @since 3.1
 */
public class SimpleFunctionRegistry implements FunctionRegistry, FunctionInspector {

	Log logger = LogFactory.getLog(BeanFactoryAwareFunctionRegistry.class);

	/**
	 * Identifies MessageConversionExceptions that happen when input can't be converted.
	 */
	public static final String COULD_NOT_CONVERT_INPUT = "Could Not Convert Input";

	/**
	 * Identifies MessageConversionExceptions that happen when output can't be converted.
	 */
	public static final String COULD_NOT_CONVERT_OUTPUT = "Could Not Convert Output";

	private final Map<Object, FunctionRegistration<Object>> registrationsByFunction = new HashMap<>();

	private final Map<String, FunctionRegistration<Object>> registrationsByName = new HashMap<>();

	private final ConversionService conversionService;

	private final CompositeMessageConverter messageConverter;

	private List<String> declaredFunctionDefinitions;

	public SimpleFunctionRegistry(ConversionService conversionService, @Nullable CompositeMessageConverter messageConverter) {
		this.conversionService = conversionService;
		this.messageConverter = messageConverter;
		this.init(System.getProperty("spring.cloud.function.definition"));
	}

	void init(String functionDefinition) {
		this.declaredFunctionDefinitions = StringUtils.hasText(functionDefinition) ? Arrays.asList(functionDefinition.split(";")) : Collections.emptyList();
		if (this.declaredFunctionDefinitions.contains(RoutingFunction.FUNCTION_NAME)) {
			Assert.isTrue(this.declaredFunctionDefinitions.size() == 1, "It is illegal to declare more then one function when using RoutingFunction");
		}
	}

	@Override
	public <T> T lookup(Class<?> type, String definition) {
		return this.lookup(definition, new String[] {});
	}

	@Override
	public int size() {
		return this.registrationsByFunction.size();
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T lookup(String definition, String... acceptedOutputTypes) {
		definition = StringUtils.hasText(definition) ? definition.replaceAll(",", "|") : "";

		boolean routing = definition.contains(RoutingFunction.FUNCTION_NAME)
				|| this.declaredFunctionDefinitions.contains(RoutingFunction.FUNCTION_NAME);

		if (!routing && this.declaredFunctionDefinitions.size() > 0) {
			if (StringUtils.hasText(definition)) {
				if (this.declaredFunctionDefinitions.size() > 1 && !this.declaredFunctionDefinitions.contains(definition)) {
					logger.warn("Attempted to access un-declared function definition '" + definition + "'. Declared functions are '" + this.declaredFunctionDefinitions
							+ "' specified via `spring.cloud.function.definition` property. If the intention is to access "
							+ "any function available in FunctionCatalog, please remove `spring.cloud.function.definition` property.");
					return null;
				}
			}
			else {
				if (this.declaredFunctionDefinitions.size() == 1) {
					definition = this.declaredFunctionDefinitions.get(0);
				}
				else if (this.declaredFunctionDefinitions.size() > 1) {
					logger.warn("Default function can not be mapped since multiple functions are declared " + this.declaredFunctionDefinitions);
					return null;
				}
				else {
					logger.warn("Default function can not be mapped since multiple functions are available in FunctionCatalog. "
							+ "Please use 'spring.cloud.function.definition' property.");
					return null;
				}
			}
		}

		Object function = this
			.proxyInvokerIfNecessary((FunctionInvocationWrapper) this.compose(null, definition, acceptedOutputTypes));
		return (T) function;
	}

	@Override
	public Set<String> getNames(Class<?> type) {
		Set<String> registeredNames = registrationsByFunction.values().stream().flatMap(reg -> reg.getNames().stream())
			.collect(Collectors.toSet());
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
	public FunctionRegistration<?> getRegistration(Object function) {
		FunctionRegistration<?> registration = this.registrationsByFunction.get(function);
		// need to do this due to the deployer not wrapping the actual target into FunctionInvocationWrapper
		// hence the lookup would need to be made by the actual target
		if (registration == null && function instanceof FunctionInvocationWrapper) {
			function = ((FunctionInvocationWrapper) function).target;
		}
		return this.registrationsByFunction.get(function);
	}

	Object locateFunction(String name) {
		return this.registrationsByName.get(name);
	}

	Type discoverFunctionType(Object function, String... names) {
		if (function instanceof RoutingFunction) {
			return this.registrationsByName.get(names[0]).getType().getType();
		}
		return FunctionTypeUtils.discoverFunctionTypeFromClass(function.getClass());
	}

	String discoverDefaultDefinitionFromRegistration() {
		String definition = null;
		if (this.registrationsByName.size() > 0) {
			Assert
				.isTrue(this.registrationsByName.size() == 1, "Found more then one function in local registry");
			definition = this.registrationsByName.keySet().iterator().next();
		}
		return definition;
	}

	String discoverDefaultDefinitionIfNecessary(String definition) {
		if (StringUtils.isEmpty(definition)) {
			definition = this.discoverDefaultDefinitionFromRegistration();
		}
		else if (!this.registrationsByName.containsKey(definition) && this.registrationsByName.size() == 1) {
			definition = this.registrationsByName.keySet().iterator().next();
		}
		else if (definition.endsWith("|")) {
			if (this.registrationsByName.size() == 2) {
				Set<String> fNames = this.getNames(null);
				definition = this.determinImpliedDefinition(fNames, definition);
			}
		}
		return definition;
	}

	String determinImpliedDefinition(Set<String> fNames, String originalDefinition) {
		if (fNames.size() == 2) {
			Iterator<String> iter = fNames.iterator();
			String n1 = iter.next();
			String n2 = iter.next();
			String[] definitionName = StringUtils.delimitedListToStringArray(originalDefinition, "|");
			if (definitionName[0].equals(n1)) {
				definitionName[1] = n2;
				originalDefinition = definitionName[0] + "|" + definitionName[1];
			}
			else {
				definitionName[1] = n1;
				originalDefinition = definitionName[0] + "|" + definitionName[1];
			}
		}
		return originalDefinition;
	}

	Type discovereFunctionTypeByName(String name) {
		return this.registrationsByName.get(name).getType().getType();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Function<?, ?> compose(Class<?> type, String definition, String... acceptedOutputTypes) {
		if (logger.isInfoEnabled()) {
			logger.info("Looking up function '" + definition + "' with acceptedOutputTypes: " + Arrays
				.asList(acceptedOutputTypes));
		}
		definition = discoverDefaultDefinitionIfNecessary(definition);
		if (StringUtils.isEmpty(definition)) {
			return null;
		}
		Function<?, ?> resultFunction = null;
		if (this.registrationsByName.containsKey(definition)) {
			Object targetFunction = this.registrationsByName.get(definition).getTarget();
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
						+ "Function available in catalog are: " + this.getNames(null));
					return null;
				}
				else {
					Type functionType = this.discovereFunctionTypeByName(name);
					if (functionType != null && functionType.toString().contains("org.apache.kafka.streams.")) {
						logger
							.debug("Kafka Streams function '" + definition + "' is not supported by spring-cloud-function.");
						return null;
					}
				}

				composedNameBuilder.append(prefix);
				composedNameBuilder.append(name);

				FunctionRegistration<Object> registration;
				Type currentFunctionType = null;

				if (function instanceof FunctionRegistration) {
					registration = (FunctionRegistration<Object>) function;
					currentFunctionType = currentFunctionType == null ? registration.getType()
						.getType() : currentFunctionType;
					function = registration.getTarget();
				}
				else {
					if (isFunctionPojo(function)) {
						Method functionalMethod = FunctionTypeUtils.discoverFunctionalMethod(function.getClass());
						currentFunctionType = FunctionTypeUtils.fromFunctionMethod(functionalMethod);
						function = this.proxyTarget(function, functionalMethod);
					}
					String[] aliasNames = this.getAliases(name).toArray(new String[] {});
					currentFunctionType = currentFunctionType == null ? this
						.discoverFunctionType(function, aliasNames) : currentFunctionType;
					registration = new FunctionRegistration<>(function, name).type(currentFunctionType);
				}

				if (function instanceof RoutingFunction) {
					registrationsByFunction.putIfAbsent(function, registration);
					registrationsByName.putIfAbsent(name, registration);
				}

				function = new FunctionInvocationWrapper(function, currentFunctionType, name, names.length > 1 ? new String[] {} : acceptedOutputTypes);

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
			((FunctionInvocationWrapper) resultFunction).acceptedOutputMimeTypes = acceptedOutputTypes;
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
				logger
					.info("Proxying POJO function: " + functionInvoker.functionDefinition + ". . ." + functionInvoker.target
						.getClass());
			}
			ProxyFactory pf = new ProxyFactory(functionInvoker.getTarget());
			pf.setProxyTargetClass(true);
			pf.setInterfaces(Function.class, Supplier.class, Consumer.class);
			pf.addAdvice(new MethodInterceptor() {
				@Override
				public Object invoke(MethodInvocation invocation) throws Throwable {
					// this will trigger the INNER PROXY
					if (ObjectUtils.isEmpty(invocation.getArguments())) {
						Object o = functionInvoker.get();
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

	Collection<String> getAliases(String key) {
		return Collections.singletonList(key);
	}

	/**
	 * Single wrapper for all Suppliers, Functions and Consumers managed by this
	 * catalog.
	 *
	 * @author Oleg Zhurakousky
	 */
	public class FunctionInvocationWrapper implements Function<Object, Object>, Consumer<Object>, Supplier<Object> {

		private final Object target;

		private final Type functionType;

		private final boolean composed;

		String[] acceptedOutputMimeTypes;

		private final String functionDefinition;

		private final Field headersField;

		FunctionInvocationWrapper(Object target, Type functionType, String functionDefinition, String... acceptedOutputMimeTypes) {
			this.target = target;
			this.composed = functionDefinition.contains("|") || target instanceof RoutingFunction;
			this.functionType = functionType;
			this.acceptedOutputMimeTypes = acceptedOutputMimeTypes;
			this.functionDefinition = functionDefinition;
			this.headersField = ReflectionUtils.findField(MessageHeaders.class, "headers");
			this.headersField.setAccessible(true);
		}

		public String getFunctionDefinition() {
			return this.functionDefinition;
		}

		@Override
		public void accept(Object input) {
			this.doApply(input, true, null);
		}

		@Override
		public Object apply(Object input) {
			return this.apply(input, null);
		}

		/**
		 * !! Experimental, may change. Is not yet intended as public API !!
		 *
		 * @param input    input value
		 * @param enricher enricher function instance
		 * @return the result
		 */
		@SuppressWarnings("rawtypes")
		public Object apply(Object input, Function<Message, Message> enricher) {
			return this.doApply(input, false, enricher);
		}

		@Override
		public Object get() {
			return this.get(null);
		}

		/**
		 * !! Experimental, may change. Is not yet intended as public API !!
		 *
		 * @param enricher enricher function instance
		 * @return the result
		 */
		@SuppressWarnings("rawtypes")
		public Object get(Function<Message, Message> enricher) {
			Object input = FunctionTypeUtils.isMono(this.functionType)
				? Mono.empty()
				: (FunctionTypeUtils.isMono(this.functionType) ? Flux.empty() : null);

			return this.doApply(input, false, enricher);
		}

		public Type getFunctionType() {
			return this.functionType;
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

		@Override
		public String toString() {
			return "definition: " + this.functionDefinition + "; type: " + this.functionType;
		}

		@SuppressWarnings({"rawtypes", "unchecked"})
		private Object invokeFunction(Object input) {
			Message incomingMessage = null;
			if (!this.functionDefinition.startsWith(RoutingFunction.FUNCTION_NAME)) {
				if (input instanceof Message && !FunctionTypeUtils.isMessage(FunctionTypeUtils.getInputType(functionType, 0))
						&& ((Message) input).getHeaders().containsKey("scf-func-name")) {
					incomingMessage = (Message) input;
					input = incomingMessage.getPayload();
				}
			}

			Object invocationResult = null;
			if (this.target instanceof Function) {
				invocationResult = ((Function) target).apply(input);
			}
			else if (this.target instanceof Supplier) {
				invocationResult = ((Supplier) target).get();
			}
			else {
				if (input instanceof Flux) {
					invocationResult = ((Flux) input).transform(flux -> {
						((Consumer) this.target).accept(flux);
						return Mono.ignoreElements((Flux) flux);
					}).then();
				}
				else if (input instanceof Mono) {
					invocationResult = ((Mono) input).transform(flux -> {
						((Consumer) this.target).accept(flux);
						return Mono.ignoreElements((Mono) flux);
					}).then();
				}
				else {
					((Consumer) this.target).accept(input);
				}
			}

			if (!(this.target instanceof Consumer) && logger.isDebugEnabled()) {
				logger
					.debug("Result of invocation of \"" + this.functionDefinition + "\" function is '" + invocationResult + "'");
			}
			if (!(invocationResult instanceof Message)) {
				if (incomingMessage != null && invocationResult != null && incomingMessage.getHeaders().containsKey("scf-func-name")) {
					invocationResult = MessageBuilder.withPayload(invocationResult)
							.copyHeaders(incomingMessage.getHeaders())
							.removeHeader(MessageHeaders.CONTENT_TYPE)
							.build();
				}
			}
			return invocationResult;
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private Object doApply(Object input, boolean consumer, Function<Message, Message> enricher) {
			if (logger.isDebugEnabled()) {
				logger.debug("Applying function: " + this.functionDefinition);
			}

			Object result;
			if (input instanceof Publisher) {
				input = this.composed ? input :
					this.convertInputPublisherIfNecessary((Publisher<?>) input, FunctionTypeUtils
						.getInputType(this.functionType, 0));
				if (FunctionTypeUtils.isReactive(FunctionTypeUtils.getInputType(this.functionType, 0))) {
					result = this.invokeFunction(input);
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
				if (!this.composed && !FunctionTypeUtils
					.isMultipleInputArguments(this.functionType) && FunctionTypeUtils.isReactive(type)) {
					Publisher<?> publisher = FunctionTypeUtils.isFlux(type)
						? input == null ? Flux.empty() : Flux.just(input)
						: input == null ? Mono.empty() : Mono.just(input);
					if (logger.isDebugEnabled()) {
						logger.debug("Invoking reactive function '" + this.functionType + "' with non-reactive input "
							+ "should at least assume reactive output (e.g., Function<String, Flux<String>> f3 = catalog.lookup(\"echoFlux\");), "
							+ "otherwise invocation will result in ClassCastException.");
					}
					result = this.invokeFunction(this.convertInputPublisherIfNecessary(publisher, FunctionTypeUtils
						.getInputType(this.functionType, 0)));
				}
				else {
					result = this.invokeFunction(this.composed ? input
						: (input == null ? input : this
						.convertInputValueIfNecessary(input, FunctionTypeUtils.getInputType(this.functionType, 0))));
				}
			}

			// Outputs will be converted only if we're told how (via  acceptedOutputMimeTypes), otherwise output returned as is.
			if (result != null && !ObjectUtils.isEmpty(this.acceptedOutputMimeTypes)) {
				result = result instanceof Publisher
					? this
					.convertOutputPublisherIfNecessary((Publisher<?>) result, enricher, this.acceptedOutputMimeTypes)
					: this.convertOutputValueIfNecessary(result, enricher, this.acceptedOutputMimeTypes);
			}

			return result;
		}

		@SuppressWarnings({"rawtypes", "unchecked"})
		private Object convertOutputValueIfNecessary(Object value, Function<Message, Message> enricher, String... acceptedOutputMimeTypes) {
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
							? this
							.convertOutputPublisherIfNecessary((Publisher<?>) outputArgument, enricher, acceptedOutputMimeTypes[i])
							: this.convertOutputValueIfNecessary(outputArgument, enricher, acceptedOutputMimeTypes[i]);
					}
					catch (ArrayIndexOutOfBoundsException e) {
						throw new IllegalStateException("The number of 'acceptedOutputMimeTypes' for function '" + this.functionDefinition
							+ "' is (" + acceptedOutputMimeTypes.length
							+ "), which does not match the number of actual outputs of this function which is (" + outputCount + ").", e);
					}

				}
				convertedValue = Tuples.fromArray(convertedInputArray);
			}
			else {
				List<MimeType> acceptedContentTypes = MimeTypeUtils
					.parseMimeTypes(acceptedOutputMimeTypes[0].toString());
				if (CollectionUtils.isEmpty(acceptedContentTypes)) {
					convertedValue = value;
				}
				else {
					for (int i = 0; i < acceptedContentTypes.size() && convertedValue == null; i++) {
						MimeType acceptedContentType = acceptedContentTypes.get(i);
						/*
						 * We need to treat Iterables differently since they may represent collection of Messages
						 * which should be converted individually
						 */
						boolean convertIndividualItem = false;
						if (value instanceof Iterable || (ObjectUtils.isArray(value) && !(value instanceof byte[]))) {
							Type outputType = FunctionTypeUtils.getOutputType(functionType, 0);
							if (outputType instanceof ParameterizedType) {
								convertIndividualItem = FunctionTypeUtils.isMessage(FunctionTypeUtils.getImmediateGenericType(outputType, 0));
							}
							else if (outputType instanceof GenericArrayType) {
								convertIndividualItem = FunctionTypeUtils.isMessage(((GenericArrayType) outputType).getGenericComponentType());
							}
						}

						if (convertIndividualItem) {
							if (ObjectUtils.isArray(value)) {
								value = Arrays.asList((Object[]) value);
							}
							AtomicReference<List<Message>> messages = new AtomicReference<List<Message>>(new ArrayList<>());
							((Iterable) value).forEach(element ->
								messages.get()
									.add((Message) convertOutputValueIfNecessary(element, enricher, acceptedContentType
										.toString())));
							convertedValue = messages.get();
						}
						else {
							convertedValue = this.convertValueToMessage(value, enricher, acceptedContentType);
						}
					}
				}
			}

			if (convertedValue == null) {
				throw new MessageConversionException(COULD_NOT_CONVERT_OUTPUT);
			}
			return convertedValue;
		}

		@SuppressWarnings({"rawtypes", "unchecked"})
		private Message convertValueToMessage(Object value, Function<Message, Message> enricher, MimeType acceptedContentType) {
			Message outputMessage = null;
			if (value instanceof Message) {
				MessageHeaders headers = ((Message) value).getHeaders();
				Map<String, Object> headersMap = (Map<String, Object>) ReflectionUtils
					.getField(this.headersField, headers);
				headersMap.put("accept", acceptedContentType);
				// Set the contentType header to the value of accept for "legacy" reasons. But, do not set the
				// contentType header to the value of accept if it is a wildcard type, as this doesn't make sense.
				// This also applies to the else branch below.
				if (acceptedContentType.isConcrete()) {
					headersMap.put(MessageHeaders.CONTENT_TYPE, acceptedContentType);
				}
			}
			else {
				MessageBuilder<Object> builder = MessageBuilder.withPayload(value)
					.setHeader("accept", acceptedContentType);
				if (acceptedContentType.isConcrete()) {
					builder.setHeader(MessageHeaders.CONTENT_TYPE, acceptedContentType);
				}
				value = builder.build();
			}
			if (enricher != null) {
				value = enricher.apply((Message) value);
			}
			outputMessage = messageConverter.toMessage(((Message) value).getPayload(), ((Message) value).getHeaders());
			return outputMessage;
		}

		@SuppressWarnings("rawtypes")
		private Publisher<?> convertOutputPublisherIfNecessary(Publisher<?> publisher, Function<Message, Message> enricher, String... acceptedOutputMimeTypes) {
			if (logger.isDebugEnabled()) {
				logger.debug("Applying type conversion on output Publisher " + publisher);
			}

			Publisher<?> result = publisher instanceof Mono
				? Mono.from(publisher)
				.map(value -> this.convertOutputValueIfNecessary(value, enricher, acceptedOutputMimeTypes))
				: Flux.from(publisher)
				.map(value -> this.convertOutputValueIfNecessary(value, enricher, acceptedOutputMimeTypes));
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
				Type rawType = FunctionTypeUtils.unwrapActualTypeByIndex(type, 0);
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
							: messageConverter.fromMessage((Message<?>) value, (Class<?>) rawType);
						if (logger.isDebugEnabled()) {
							logger.debug("Converted from Message: " + convertedValue);
						}

						if (FunctionTypeUtils.isMessage(type) || ((Message<?>) value).getHeaders().containsKey("scf-func-name")) {
							convertedValue = MessageBuilder.withPayload(convertedValue)
									.copyHeaders(((Message<?>) value).getHeaders()).build();
						}
					}
					else if (!FunctionTypeUtils.isMessage(type)) {
						convertedValue = ((Message<?>) convertedValue).getPayload();
					}
				}
				else if (rawType instanceof Class<?>) { // see AWS adapter with WildardTypeImpl and Azure with Voids
					if (this.isJson(value)) {
						convertedValue = messageConverter
								.fromMessage(new GenericMessage<Object>(value), (Class<?>) rawType);
					}
					else {
						try {
							convertedValue = conversionService.convert(value, (Class<?>) rawType);
						}
						catch (Exception e) {
							if (value instanceof String || value instanceof byte[]) {
								convertedValue = messageConverter
									.fromMessage(new GenericMessage<Object>(value), (Class<?>) rawType);
							}
						}
					}
				}
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Converted input value " + convertedValue);
			}
			if (convertedValue == null) {
				throw new MessageConversionException(COULD_NOT_CONVERT_INPUT);
			}
			return convertedValue;
		}

		private boolean isJson(Object value) {
			String v = value instanceof byte[]
					? new String((byte[]) value, StandardCharsets.UTF_8)
							: (value instanceof String ? (String) value : null);
			if (v != null && JsonMapper.isJsonString(v)) {
				return true;
			}
			return false;
		}

		private boolean messageNeedsConversion(Type rawType, Message<?> message) {
			Boolean skipConversion = message.getHeaders().containsKey(FunctionProperties.SKIP_CONVERSION_HEADER)
				? message.getHeaders().get(FunctionProperties.SKIP_CONVERSION_HEADER, Boolean.class)
				: false;
			if (skipConversion) {
				return false;
			}
			return rawType instanceof Class<?>
				&& !(message.getPayload() instanceof Optional)
				&& !(message.getPayload().getClass().isAssignableFrom(((Class<?>) rawType)));
		}
	}
}
