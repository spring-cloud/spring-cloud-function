/*
 * Copyright 2019-2023 the original author or authors.
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
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
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

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.function.cloudevent.CloudEventMessageUtils;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.FunctionProperties.FunctionConfigurationProperties;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.PostProcessingFunction;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.cloud.function.core.FunctionInvocationHelper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;


/**
 * Implementation of {@link FunctionCatalog} and {@link FunctionRegistry} which
 * does not depend on Spring's {@link BeanFactory}.
 * Each function must be registered with it explicitly to benefit from features
 * such as type conversion, composition, POJO etc.
 *
 * @author Oleg Zhurakousky
 * @author Roman Samarev
 * @author Soby Chacko
 * @author Chris Bono
 */
public class SimpleFunctionRegistry implements FunctionRegistry {
	protected Log logger = LogFactory.getLog(this.getClass());
	/*
	 * - do we care about FunctionRegistration after it's been registered? What additional value does it bring?
	 *
	 */

	private final Set<FunctionRegistration<?>> functionRegistrations = new CopyOnWriteArraySet<>();

	private final Map<String, FunctionInvocationWrapper> wrappedFunctionDefinitions = new HashMap<>();

	private final ConversionService conversionService;

	private final CompositeMessageConverter messageConverter;

	private final JsonMapper jsonMapper;

	private final FunctionInvocationHelper<Message<?>> functionInvocationHelper;

	private final FunctionProperties functionProperties;

	@Autowired(required = false)
	private FunctionAroundWrapper functionAroundWrapper;

	public SimpleFunctionRegistry(ConversionService conversionService, CompositeMessageConverter messageConverter, JsonMapper jsonMapper,
			@Nullable FunctionProperties functionProperties,
			@Nullable FunctionInvocationHelper<Message<?>> functionInvocationHelper) {
		Assert.notNull(messageConverter, "'messageConverter' must not be null");
		Assert.notNull(jsonMapper, "'jsonMapper' must not be null");
		this.conversionService = conversionService;
		this.jsonMapper = jsonMapper;
		this.messageConverter = messageConverter;
		this.functionInvocationHelper = functionInvocationHelper;
		this.functionProperties = functionProperties;
	}

	/**
	 * Will add provided {@link MessageConverter}s to the head of the stack of the existing MessageConverters.
	 *
	 * @param messageConverters list of {@link MessageConverter}s.
	 */
	public void addMessageConverters(Collection<MessageConverter> messageConverters) {
		if (!CollectionUtils.isEmpty(messageConverters)) {
			this.messageConverter.getConverters().addAll(0, messageConverters);
		}
	}

	public SimpleFunctionRegistry(ConversionService conversionService, CompositeMessageConverter messageConverter, JsonMapper jsonMapper) {
		this(conversionService, messageConverter, jsonMapper, null, null);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T lookup(Class<?> type, String functionDefinition, String... expectedOutputMimeTypes) {
		functionDefinition = this.normalizeFunctionDefinition(functionDefinition);
		FunctionInvocationWrapper function = this.doLookup(type, functionDefinition, expectedOutputMimeTypes);
		if (logger.isDebugEnabled()) {
			if (function != null) {
				logger.debug("Located function: " + function);
			}
			else {
				logger.debug("Failed to locate function: " + functionDefinition);
			}
		}
		return (T) function;
	}

	@Override
	public <T> void register(FunctionRegistration<T> registration) {
		if (!isRegistrationEligible(registration)) {
			return;
		}
		Assert.notNull(registration, "'registration' must not be null");
		if (logger.isDebugEnabled()) {
			logger.debug("Registering function " + registration.getNames());
		}
		this.functionRegistrations.add(registration);
	}

	@SuppressWarnings("rawtypes")
	private boolean isRegistrationEligible(FunctionRegistration registration) {
		if (this.functionProperties != null) {
			for (String definition : this.functionProperties.getIneligibleDefinitions()) {
				if (registration.getTarget().getClass().getName().equals(definition)) {
					return false;
				}
				else if (registration.getNames().contains(definition) || registration.getTarget().getClass().getName().contains(definition)) {
					return false;
				}
			}
		}
		return true;
	}

	boolean isFunctionDefinitionEligible(String functionDefinition) {
		if (this.functionProperties != null) {
			for (String definition : this.functionProperties.getIneligibleDefinitions()) {
				if (functionDefinition.contains(definition)) {
					return false;
				}
			}
		}
		return true;
	}

	//-----

	@Override
	public Set<String> getNames(Class<?> type) {
		return this.functionRegistrations.stream().flatMap(fr -> fr.getNames().stream()).collect(Collectors.toSet());
	}

	@Override
	public int size() {
		return this.functionRegistrations.size();
	}

	/*
	 *
	 */
	protected boolean containsFunction(String functionName) {
		return this.functionRegistrations.stream().anyMatch(reg -> reg.getNames().contains(functionName));
	}

	/*
	 *
	 */
	@SuppressWarnings("unchecked")
	<T> T doLookup(Class<?> type, String functionDefinition, String[] expectedOutputMimeTypes) {
		FunctionInvocationWrapper function = this.wrappedFunctionDefinitions.get(functionDefinition);
		if (function == null) {
			function = this.compose(type, functionDefinition);
		}

		if (function != null) {
			if (!ObjectUtils.isEmpty(expectedOutputMimeTypes)) {
				function.expectedOutputContentType = expectedOutputMimeTypes;
			}
		}
		else if (logger.isDebugEnabled()) {
			logger.debug("Function '" + functionDefinition + "' is not found in cache");
		}

		return (T) function;
	}

	/**
	 * This method will make sure that if there is only one function in catalog
	 * it can be looked up by any name or no name.
	 * It does so by attempting to determine the default function name
	 * (the only function in catalog) and checking if it matches the provided name
	 * replacing it if it does not.
	 */
	String normalizeFunctionDefinition(String functionDefinition) {
		functionDefinition = StringUtils.hasText(functionDefinition)
				? functionDefinition.replaceAll(",", "|")
				: System.getProperty(FunctionProperties.FUNCTION_DEFINITION, "");

		Set<String> names = this.getNames(null);
		if (!names.contains(functionDefinition)) {
			List<String> eligibleFunction = names.stream()
					.filter(name -> !RoutingFunction.FUNCTION_NAME.equals(name))
					.filter(name -> !RoutingFunction.DEFAULT_ROUTE_HANDLER.equals(name))
					.collect(Collectors.toList());
			if (eligibleFunction.size() == 1
					&& !eligibleFunction.get(0).equals(functionDefinition)
					&& !functionDefinition.contains("|")
					&& !eligibleFunction.get(0).startsWith("&")) {
				functionDefinition = eligibleFunction.get(0);
			}
		}
		return functionDefinition;
	}

	/*
	 *
	 */
	private FunctionInvocationWrapper findFunctionInFunctionRegistrations(String functionName) {
		FunctionRegistration<?> functionRegistration = this.functionRegistrations.stream()
				.filter(fr -> fr.getNames().contains(functionName))
				.findFirst()
				.orElseGet(() -> null);
		FunctionInvocationWrapper function = functionRegistration != null
				? this.invocationWrapperInstance(functionName, functionRegistration.getTarget(), functionRegistration.getType())
				: null;
		if (functionRegistration != null) {
			Object userFunction = functionRegistration.getUserFunction();
			if (userFunction instanceof BiConsumer && function != null) {
				function.setWrappedBiConsumer(true);
			}
		}
		if (functionRegistration != null && functionRegistration.getProperties().containsKey("singleton")) {
			try {
				function.isSingleton = Boolean.parseBoolean(functionRegistration.getProperties().get("singleton"));
			}
			catch (Exception e) {
				// ignore
			}
		}
		return function;
	}

	/*
	 *
	 */
	private FunctionInvocationWrapper compose(Class<?> type, String functionDefinition) {
		String[] functionNames = StringUtils.delimitedListToStringArray(functionDefinition.replaceAll(",", "|").trim(), "|");
		FunctionInvocationWrapper composedFunction = null;

		for (String functionName : functionNames) {
			FunctionInvocationWrapper function = this.findFunctionInFunctionRegistrations(functionName);
			if (function == null) {
				return null;
			}
			else {
				if (composedFunction == null) {
					composedFunction = function;
				}
				else {
					FunctionInvocationWrapper andThenFunction =
							invocationWrapperInstance(functionName, function.getTarget(), function.inputType, function.outputType);
					composedFunction = (FunctionInvocationWrapper) composedFunction.andThen((Function<Object, Object>) andThenFunction);
				}
				composedFunction = this.enrichInputIfNecessary(composedFunction);
				composedFunction = this.enrichOutputIfNecessary(composedFunction);
				if (composedFunction.isSingleton) {
					this.wrappedFunctionDefinitions.put(composedFunction.functionDefinition, composedFunction);
				}
			}
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Composed function " + composedFunction);
		}
		return composedFunction;
	}

	private FunctionInvocationWrapper enrichInputIfNecessary(FunctionInvocationWrapper composedFunction) {
		if (this.functionProperties == null) {
			return composedFunction;
		}
		String functionDefinition = composedFunction.getFunctionDefinition();
		Map<String, FunctionConfigurationProperties> configurationProperties = this.functionProperties.getConfiguration();
		if (!CollectionUtils.isEmpty(configurationProperties)) {
			FunctionConfigurationProperties configuration =  configurationProperties
					.get(functionDefinition.replace("|", "").replace(",", ""));
			if (configuration != null) {
				if (!CollectionUtils.isEmpty(configuration.getInputHeaderMappingExpression())) {
					BeanFactoryResolver beanResolver = this.functionProperties.getApplicationContext() != null
							? new BeanFactoryResolver(this.functionProperties.getApplicationContext())
							: null;
					HeaderEnricher enricher = new HeaderEnricher(configuration.getInputHeaderMappingExpression(), beanResolver);
					FunctionInvocationWrapper w = new FunctionInvocationWrapper("inputHeaderEnricher", enricher, Message.class, Message.class);
					composedFunction = (FunctionInvocationWrapper) w.andThen((Function<Object, Object>) composedFunction);
					composedFunction.functionDefinition = functionDefinition;
				}
			}
		}
		return composedFunction;
	}

	private FunctionInvocationWrapper enrichOutputIfNecessary(FunctionInvocationWrapper composedFunction) {
		if (this.functionProperties == null) {
			return composedFunction;
		}
		String functionDefinition = composedFunction.getFunctionDefinition();
		Map<String, FunctionConfigurationProperties> configurationProperties = this.functionProperties.getConfiguration();
		if (!CollectionUtils.isEmpty(configurationProperties)) {
			FunctionConfigurationProperties configuration =  configurationProperties
					.get(functionDefinition.replace("|", "").replace(",", ""));
			if (configuration != null) {
				if (!CollectionUtils.isEmpty(configuration.getOutputHeaderMappingExpression())) {
					BeanFactoryResolver beanResolver = this.functionProperties.getApplicationContext() != null
							? new BeanFactoryResolver(this.functionProperties.getApplicationContext())
							: null;
					HeaderEnricher enricher = new HeaderEnricher(configuration.getOutputHeaderMappingExpression(), beanResolver);
					Type mesageType = ResolvableType.forClassWithGenerics(Message.class, Object.class).getType();
					FunctionInvocationWrapper enricherWrapper = new FunctionInvocationWrapper("outputHeaderEnricher", enricher, mesageType, mesageType);
					composedFunction = (FunctionInvocationWrapper) composedFunction.andThen((Function<Object, Object>) enricherWrapper);
					composedFunction.functionDefinition = functionDefinition;
				}
			}
		}
		return composedFunction;
	}

	/*
	 *
	 */
	private FunctionInvocationWrapper invocationWrapperInstance(String functionDefinition, Object target, Type inputType, Type outputType) {
		return new FunctionInvocationWrapper(functionDefinition, target, inputType, outputType);
	}

	/*
	 *
	 */
	private FunctionInvocationWrapper invocationWrapperInstance(String functionDefinition, Object target, Type functionType) {
		return invocationWrapperInstance(functionDefinition, target,
				FunctionTypeUtils.isSupplier(functionType) ? null : FunctionTypeUtils.getInputType(functionType),
				FunctionTypeUtils.getOutputType(functionType));
	}

	/**
	 *
	 */
	@SuppressWarnings("rawtypes")
	public class FunctionInvocationWrapper implements Function<Object, Object>, Consumer<Object>, Supplier<Object>, Runnable {

		private final Object target;

		private Type inputType;

		private final Type outputType;

		private String functionDefinition;

		private boolean composed;

		private boolean message;

		private String[] expectedOutputContentType;

		private boolean skipInputConversion;

		private boolean skipOutputConversion;

		private boolean isSingleton = true;

		private boolean propagateInputHeaders;

		private boolean wrapped;

		private final ThreadLocal<Message<Object>> unconvertedResult = new ThreadLocal<>();

		private PostProcessingFunction postProcessor;

		/*
		 * This is primarily to support Stream's ability to access
		 * un-converted payload (e.g., to evaluate expression on some attribute of a payload)
		 * It is not intended to remain here and will be removed as soon as particular elements
		 * of stream will be refactored to address this.
		 */
		private Function<Object, Object> enhancer;

		private boolean wrappedBiConsumer;

		FunctionInvocationWrapper(String functionDefinition,  Object target, Type inputType, Type outputType) {
			if (target instanceof PostProcessingFunction) {
				this.postProcessor = (PostProcessingFunction) target;
			}
			this.target = target;
			this.inputType = this.normalizeType(inputType);
			this.outputType = this.normalizeType(outputType);
			this.functionDefinition = functionDefinition;
			this.message = this.inputType != null && FunctionTypeUtils.isMessage(this.inputType);
			if (functionProperties != null) {
				Map<String, FunctionConfigurationProperties> funcConfiguration = functionProperties.getConfiguration();
				if (!CollectionUtils.isEmpty(funcConfiguration)) {
					FunctionConfigurationProperties configuration = funcConfiguration.get(functionDefinition);
					if (configuration != null) {
						propagateInputHeaders = configuration.isCopyInputHeaders();
					}
				}
			}
		}

		@SuppressWarnings("unchecked")
		public void postProcess() {
			if (this.postProcessor != null) {
				Message result = this.unconvertedResult.get();
				if (result != null) {
					try {
						this.postProcessor.postProcess(result);
					}
					catch (Exception ex) {
						logger.warn("Failed to post process function "
								+ this.functionDefinition  + "; Result of the invocation before post processing is " + result, ex);
					}
					finally {
						this.unconvertedResult.remove();
					}
				}
			}
		}

		public boolean isWrappedBiConsumer() {
			return wrappedBiConsumer;
		}

		public void setWrappedBiConsumer(boolean wrappedBiConsumer) {
			this.wrappedBiConsumer = wrappedBiConsumer;
		}

		public boolean isSkipOutputConversion() {
			return skipOutputConversion;
		}


		public boolean isPrototype() {
			return !this.isSingleton;
		}

		public void setSkipInputConversion(boolean skipInputConversion) {
			if (logger.isDebugEnabled() && skipInputConversion) {
				logger.debug("'skipInputConversion' was explicitely set to true. No input conversion will be attempted");
			}
			this.skipInputConversion = skipInputConversion;
		}

		public void setSkipOutputConversion(boolean skipOutputConversion) {
			if (logger.isDebugEnabled() && skipOutputConversion) {
				logger.debug("'skipOutputConversion' was explicitely set to true. No output conversion will be attempted");
			}
			this.skipOutputConversion = skipOutputConversion;
		}

		/**
		 * !!! INTERNAL USE ONLY !!!
		 * This is primarily to support s-c-Stream's ability to access
		 * un-converted payload (e.g., to evaluate expression on some attribute of a payload)
		 * It is not intended to remain here and will be removed as soon as particular elements
		 * of stream will be refactored to address this.
		 */
		public Function<Object, Object> getEnhancer() {
			return this.enhancer;
		}

		public Type getOutputType() {
			return this.outputType;
		}

		/**
		 * !!! INTERNAL USE ONLY !!!
		 * This is primarily to support s-c-Stream's ability to access
		 * un-converted payload (e.g., to evaluate expression on some attribute of a payload)
		 * It is not intended to remain here and will be removed as soon as particular elements
		 * of stream will be refactored to address this.
		 */
		public void setEnhancer(Function<Object, Object> enhancer) {
			this.enhancer = enhancer;
		}

		public Object getTarget() {
			return target;
		}

		public Type getInputType() {
			return this.inputType;
		}

		/**
		 * Return the actual {@link Type} of the item of the provided type.
		 * This method is context specific and is not a general purpose utility method. The context is that the provided
		 * {@link Type} may represent the input/output of a function where such type could be wrapped in
		 * {@link Message}, {@link Flux} or {@link Mono}, so this method returns generic value of such type or itself if not wrapped.
		 * @param type typically input or output Type of the function (see {@link #getInputType()} or {@link #getOutputType()}.
		 * @return the type of the item if wrapped otherwise the provided type.
		 */
		public Type getItemType(Type type) {
			if (FunctionTypeUtils.isPublisher(type) || FunctionTypeUtils.isMessage(type) || FunctionTypeUtils.isTypeCollection(type)) {
				type = FunctionTypeUtils.getGenericType(type);
			}
			if (FunctionTypeUtils.isMessage(type)) {
				type = FunctionTypeUtils.getGenericType(type);
			}
			return type;
		}

		public Class<?> getRawOutputType() {
			return this.outputType == null ? null : FunctionTypeUtils.getRawType(this.outputType);
		}

		public Class<?> getRawInputType() {
			return this.inputType == null ? null : FunctionTypeUtils.getRawType(this.inputType);
		}

		/**
		 *
		 */
		@Override
		public Object apply(Object input) {
			if (logger.isDebugEnabled() && !(input  instanceof Publisher)) {
				logger.debug("Invoking function " + this);
			}

			Object result;
			if (functionAroundWrapper != null && !this.wrapped) {
				this.wrapped = true;
				result = functionAroundWrapper.apply(input, this);
			}
			else {
				result = this.doApply(input);
			}

			return result;
		}

		@Override
		public Object get() {
			return this.apply(null);
		}

		@Override
		public void accept(Object input) {
			this.apply(input);
		}

		@Override
		public void run() {
			this.apply(null);
		}

		public boolean isConsumer() {
			return this.outputType == null;
		}

		public boolean isSupplier() {
			return this.inputType == null;
		}

		public boolean isFunction() {
			return this.inputType != null && this.outputType != null;
		}

		public boolean isInputTypePublisher() {
			return this.isTypePublisher(this.inputType);
		}

		public boolean isOutputTypePublisher() {
			return this.isTypePublisher(this.outputType);
		}

		public boolean isInputTypeMessage() {
			boolean b = this.message || this.isRoutingFunction();
			return b;
		}

		public boolean isOutputTypeMessage() {
			return FunctionTypeUtils.isMessage(this.outputType);
		}


		public boolean isRoutingFunction() {
			return this.target instanceof RoutingFunction;
		}

		/*
		 *
		 */
		@SuppressWarnings("unchecked")
		@Override
		public <V> Function<Object, V> andThen(Function<? super Object, ? extends V> after) {
			Assert.isTrue(after instanceof FunctionInvocationWrapper, "Composed function must be an instanceof FunctionInvocationWrapper.");

			if (FunctionTypeUtils.isMultipleArgumentType(this.inputType)
					|| FunctionTypeUtils.isMultipleArgumentType(this.outputType)
					|| FunctionTypeUtils.isMultipleArgumentType(((FunctionInvocationWrapper) after).inputType)
					|| FunctionTypeUtils.isMultipleArgumentType(((FunctionInvocationWrapper) after).outputType)) {
				throw new UnsupportedOperationException("Composition of functions with multiple arguments is not supported at the moment");
			}

			this.setSkipOutputConversion(true);
			((FunctionInvocationWrapper) after).setSkipOutputConversion(true);
			Function rawComposedFunction = v -> ((FunctionInvocationWrapper) after).doApply(doApply(v));

			FunctionInvocationWrapper afterWrapper = (FunctionInvocationWrapper) after;

			Type composedFunctionType;
			if (afterWrapper.outputType == null) {
				composedFunctionType = (this.inputType == null) ?
					ResolvableType.forClassWithGenerics(Supplier.class, ResolvableType.forType(Object.class)).getType() :
					ResolvableType.forClassWithGenerics(Consumer.class, ResolvableType.forType(this.inputType)).getType();
			}
			else if (this.inputType == null && afterWrapper.outputType != null) {
				ResolvableType composedOutputType;
				if (FunctionTypeUtils.isFlux(this.outputType)) {
					composedOutputType = ResolvableType.forClassWithGenerics(Flux.class, ResolvableType.forType(afterWrapper.outputType));
				}
				else if (FunctionTypeUtils.isMono(this.outputType)) {
					composedOutputType = ResolvableType.forClassWithGenerics(Mono.class, ResolvableType.forType(afterWrapper.outputType));
				}
				else {
					composedOutputType = ResolvableType.forType(afterWrapper.outputType);
				}

				composedFunctionType = ResolvableType.forClassWithGenerics(Supplier.class, composedOutputType).getType();
			}
			else if (this.outputType == null) {
				throw new IllegalArgumentException("Can NOT compose anything with Consumer");
			}
			else {
				composedFunctionType = ResolvableType.forClassWithGenerics(Function.class,
						ResolvableType.forType(this.inputType),
						ResolvableType.forType(((FunctionInvocationWrapper) after).outputType)).getType();
			}

			String composedName = this.functionDefinition + "|" + afterWrapper.functionDefinition;
			FunctionInvocationWrapper composedFunction = invocationWrapperInstance(composedName, rawComposedFunction, composedFunctionType);
			composedFunction.composed = true;
			if (((FunctionInvocationWrapper) after).target instanceof PostProcessingFunction) {
				composedFunction.postProcessor = (PostProcessingFunction) ((FunctionInvocationWrapper) after).target;
			}

			return (Function<Object, V>) composedFunction;
		}

		/**
		 * Returns the definition of this function.
		 * @return function definition
		 */
		public String getFunctionDefinition() {
			return this.functionDefinition;
		}

		/*
		 *
		 */
		@Override
		public String toString() {
			return this.functionDefinition + (this.isComposed() ? "" : "<" + this.inputType + ", " + this.outputType + ">");
		}

		/**
		 * Returns true if this function wrapper represents a composed function.
		 * @return true if this function wrapper represents a composed function otherwise false
		 */
		boolean isComposed() {
			return this.composed;
		}

		/*
		 *
		 */
		@SuppressWarnings("unchecked")
		Object doApply(Object input) {
			Object result;

			input = this.fluxifyInputIfNecessary(input);

			Object convertedInput = this.convertInputIfNecessary(input, this.inputType);

			if (this.isRoutingFunction() || this.isComposed()) {
				result = ((Function) this.target).apply(convertedInput);
			}
			else if (this.isSupplier()) {
				result = ((Supplier) this.target).get();
			}
			else if (this.isConsumer()) {
				result = this.invokeConsumer(convertedInput);
			}
			else { // Function
				result = this.invokeFunction(convertedInput);
			}

			if (this.postProcessor != null) {
				this.unconvertedResult.set((Message<Object>) result);
			}

			if (result != null && this.outputType != null) {
				result = this.convertOutputIfNecessary(result, this.outputType, this.expectedOutputContentType);
			}
			return result;
		}

		/*
		 *
		 */
		private boolean isTypePublisher(Type type) {
			return type != null && FunctionTypeUtils.isPublisher(type);
		}

		/**
		 * Will return Object.class if type is represented as TypeVariable(T) or WildcardType(?).
		 */
		private Type normalizeType(Type type) {
			if (type != null) {
				return !(type instanceof TypeVariable) && !(type instanceof WildcardType) ? type : Object.class;
			}
			return type;
		}

		/*
		 *
		 */
		private Class<?> getRawClassFor(@Nullable Type type) {
			return type instanceof TypeVariable || type instanceof WildcardType
					? Object.class
					: FunctionTypeUtils.getRawType(type);
		}

		/**
		 * Will wrap the result in a Message if necessary and will copy input headers to the output message.
		 */
		private Object enrichInvocationResultIfNecessary(Object input, Object result) {
			if (result != null && !(result instanceof Publisher) && input instanceof Message) {
				if (result instanceof Message) {
					if (functionInvocationHelper != null && CloudEventMessageUtils.isCloudEvent(((Message) input))) {
						result = functionInvocationHelper.postProcessResult(result, (Message) input);
					}
				}
				else {
					if (functionInvocationHelper != null && CloudEventMessageUtils.isCloudEvent(((Message) input))) {
						result = functionInvocationHelper.postProcessResult(result, (Message) input);
					}
					else if (!FunctionTypeUtils.isCollectionOfMessage(this.outputType)) {
						result = MessageBuilder.withPayload(result).copyHeaders(this.sanitizeHeaders(((Message) input).getHeaders())).build();
					}
				}
			}
			return result;
		}

		/*
		 * Will ensure no headers with null values are copied.
		 */
		private Map<String, Object> sanitizeHeaders(MessageHeaders headers) {
			Map<String, Object> sanitizedHeaders = new HashMap<>();
			headers.forEach((k, v) -> {
				if (v != null) {
					sanitizedHeaders.put(k, v);
				}
			});
			return sanitizedHeaders;
		}

		@SuppressWarnings("unchecked")
		private Object fluxifyInputIfNecessary(Object input) {
			if (input instanceof Message && !((Message) input).getHeaders().containsKey("user-agent") && this.isConsumer() && !this.isInputTypePublisher()) {
				return input;
			}
			if (FunctionTypeUtils.isMultipleArgumentType(this.inputType)) {
				return input;
			}

			if (!this.isRoutingFunction() && !(input instanceof Publisher)) {
				Object payload = input;
				var treatPayloadAsPlainText = false;
				if (input instanceof Message msg) {
					if (msg.getHeaders().containsKey("payload")) {
						payload = msg.getHeaders().get("payload");
					}
					else {
						payload = msg.getPayload();
					}
					treatPayloadAsPlainText = contentTypeHeaderValue(msg).equals(MimeTypeUtils.TEXT_PLAIN_VALUE);
				}

				if ((!treatPayloadAsPlainText && JsonMapper.isJsonStringRepresentsCollection(payload))
						&& !FunctionTypeUtils.isTypeCollection(this.inputType)
						&& !FunctionTypeUtils.isTypeArray(this.inputType)) {
					MessageHeaders headers = ((Message) input).getHeaders();
					// Original version
//                     Collection collectionPayload = jsonMapper.fromJson(payload, Collection.class);
					// Fix for SmartB
					// Collection Type is needed by kotlin serializer to deserialize object
					Type genType = FunctionTypeUtils.getGenericType(this.inputType);
					ResolvableType resolvableType = ResolvableType.forType(genType);
					if (resolvableType.toClass() == Message.class) {
						resolvableType = resolvableType.getGeneric(0);
					}
					ResolvableType listType = ResolvableType.forClassWithGenerics(List.class, resolvableType);
					Collection collectionPayload = jsonMapper.fromJson(payload, listType.getType());
					// SmartB End Of Modification

					Class inputClass = FunctionTypeUtils.getRawType(this.inputType);
					if (this.isInputTypeMessage()) {
						inputClass = FunctionTypeUtils.getRawType(FunctionTypeUtils.getImmediateGenericType(this.inputType, 0));
					}

					if (!inputClass.isAssignableFrom(Object.class) && !inputClass.isAssignableFrom(byte[].class)) {
						logger.debug("Converting JSON string representing collection to a list of Messages. Function '"
								+ this + "' will be invoked iteratively");
						input = collectionPayload.stream()
								.map(p -> MessageBuilder.withPayload(p).copyHeaders(headers).build())
								.collect(Collectors.toList());
					}
				}
			}

			if (this.isTypePublisher(this.inputType) && !(input instanceof Publisher)) {
				if (input == null) {
					input = FunctionTypeUtils.isMono(this.inputType) ? Mono.empty() : Flux.empty();
				}
				else if (input instanceof Message && ((Message) input).getPayload() instanceof Iterable) {
					input = FunctionTypeUtils.isMono(this.inputType) ? Mono.just(input) : Flux.just(input).flatMap(v -> {
						if (logger.isDebugEnabled()) {
							logger.debug("Creating Flux from Iterable: " + ((Message) v).getPayload());
						}
						return Flux.fromIterable((Iterable) ((Message) v).getPayload());
					});
				}
				else if (input instanceof Iterable) {
					input = FunctionTypeUtils.isMono(this.inputType) ? Mono.just(input) : Flux.fromIterable((Iterable) input);

				}
				else {
					input = FunctionTypeUtils.isMono(this.inputType) ? Mono.just(input) : Flux.just(input);
				}
			}
			else if (!(input instanceof Publisher) && input instanceof Iterable && !FunctionTypeUtils.isTypeCollection(this.inputType)) {
				input = Flux.fromIterable((Iterable) input);
			}
			return input;
		}

		private String contentTypeHeaderValue(Message<?> msg) {
			var contentType = msg.getHeaders().get(MessageHeaders.CONTENT_TYPE);
			if (contentType == null) {
				contentType = msg.getHeaders().get(HttpHeaders.CONTENT_TYPE);
				if (contentType == null) {
					contentType = msg.getHeaders().get(HttpHeaders.CONTENT_TYPE.toLowerCase());
				}
			}
			return Objects.toString(contentType);
		}

		@SuppressWarnings("unchecked")
		private Object invokeFunction(Object convertedInput) {
			Object result;
			if (!this.isTypePublisher(this.inputType) && convertedInput instanceof Publisher) {
				result = convertedInput instanceof Mono
						? Mono.from((Publisher) convertedInput).map(value -> this.invokeFunctionAndEnrichResultIfNecessary(value))
							.doOnError(ex -> logger.error("Failed to invoke function '" + this.functionDefinition + "'", (Throwable) ex))
						: Flux.from((Publisher) convertedInput).map(value -> this.invokeFunctionAndEnrichResultIfNecessary(value))
							.doOnError(ex -> logger.error("Failed to invoke function '" + this.functionDefinition + "'", (Throwable) ex));
			}
			else {
				result = this.invokeFunctionAndEnrichResultIfNecessary(convertedInput);
				if (result instanceof Flux) {
					result = ((Flux) result).doOnError(ex -> logger.error("Failed to invoke function '"
							+ this.functionDefinition + "'", (Throwable) ex));
				}
				else if (result instanceof Mono) {
					result = ((Mono) result).doOnError(ex -> logger.error("Failed to invoke function '"
							+ this.functionDefinition + "'", (Throwable) ex));
				}
			}
			return result;
		}

		/*
		 *
		 */
		@SuppressWarnings("unchecked")
		private Object invokeFunctionAndEnrichResultIfNecessary(Object value) {
			AtomicReference<Message<?>> firstInputMessage = new AtomicReference<>();

			Object inputValue;
			if (value instanceof Flux) {
				inputValue = ((Flux) value).map(v -> {
					if (v instanceof OriginalMessageHolder && firstInputMessage.get() == null) {
						firstInputMessage.set(((OriginalMessageHolder) v).getOriginalMessage());
					}
					return this.extractValueFromOriginalValueHolderIfNecessary(v);
				});
			}
			else if (value instanceof Mono) {
				inputValue = ((Mono) value).map(v -> {
					if (v instanceof OriginalMessageHolder) {
						firstInputMessage.set(((OriginalMessageHolder) v).getOriginalMessage());
					}
					return this.extractValueFromOriginalValueHolderIfNecessary(v);
				});
			}
			else {
				inputValue = this.extractValueFromOriginalValueHolderIfNecessary(value);
			}

			if (!(this.target instanceof PassThruFunction) && inputValue instanceof Message && !this.isInputTypeMessage()) {
				inputValue = ((Message) inputValue).getPayload();
			}

			if (logger.isDebugEnabled()) {
				logger.debug("Invoking function: " + this + "with input type: " + this.getInputType());
			}

			Object result;
			if (inputValue != null && inputValue.getClass().getName().equals("org.springframework.kafka.support.KafkaNull")) {
				result = ((Function) this.target).apply(null);
			}
			else {
				result = ((Function) this.target).apply(inputValue);
			}

			if (result instanceof Publisher && functionInvocationHelper != null) {
				result = this.postProcessFunction((Publisher) result, firstInputMessage);
			}

			return value instanceof OriginalMessageHolder
					? this.enrichInvocationResultIfNecessary(((OriginalMessageHolder) value).getOriginalMessage(), result)
					: result;
		}

		@SuppressWarnings("unchecked")
		private Publisher postProcessFunction(Publisher result, AtomicReference<Message<?>> firstInputMessage) {
			if (FunctionTypeUtils.isPublisher(this.inputType) && FunctionTypeUtils.isPublisher(this.outputType)) {
				if (!FunctionTypeUtils.getRawType(FunctionTypeUtils.getImmediateGenericType(this.inputType, 0))
						.isAssignableFrom(Void.class)
					&& !FunctionTypeUtils.getRawType(FunctionTypeUtils.getImmediateGenericType(this.outputType, 0))
						.isAssignableFrom(Void.class)) {

					if (result instanceof Mono) {
						return Mono.from((result)).map(v -> {
							if (firstInputMessage.get() != null && CloudEventMessageUtils
									.isCloudEvent(firstInputMessage.get())) {
								return functionInvocationHelper.postProcessResult(v,
										firstInputMessage.get());
							}
							return v;
						});
					}
					else {
						return Flux.from((result)).map(v -> {
							if (firstInputMessage.get() != null && CloudEventMessageUtils
									.isCloudEvent(firstInputMessage.get())) {
								return functionInvocationHelper.postProcessResult(v,
										firstInputMessage.get());
							}
							return v;
						});
					}
				}
			}

			return result;
		}

		/*
		 *
		 */
		@SuppressWarnings("unchecked")
		private Object invokeConsumer(Object convertedInput) {
			Object result = null;
			if (this.isTypePublisher(this.inputType)) {
				if (convertedInput instanceof Flux) {
					result = ((Flux) convertedInput)
							.transform(flux -> {
								flux =  Flux.from((Publisher) flux).map(v -> this.extractValueFromOriginalValueHolderIfNecessary(v));
								((Consumer) this.target).accept(flux);
								return Mono.ignoreElements((Flux) flux);
							}).then();
				}
				else {
					result = ((Mono) convertedInput)
							.transform(mono -> {
								mono =  Mono.from((Publisher) mono).map(v -> this.extractValueFromOriginalValueHolderIfNecessary(v));
								((Consumer) this.target).accept(mono);
								return Mono.ignoreElements((Mono) mono);
							}).then();
				}
			}
			else if (convertedInput instanceof Publisher) {
				result = convertedInput instanceof Mono
						? Mono.from((Publisher) convertedInput)
								.map(v -> this.extractValueFromOriginalValueHolderIfNecessary(v))
								.doOnNext((Consumer) this.target).then()
						: Flux.from((Publisher) convertedInput)
								.map(v -> this.extractValueFromOriginalValueHolderIfNecessary(v))
								.doOnNext((Consumer) this.target).then();
			}
			else {
				Object extractedValue = this.extractValueFromOriginalValueHolderIfNecessary(convertedInput);
				((Consumer) this.target).accept(extractedValue);
			}
			return result;
		}

		private Object extractValueFromOriginalValueHolderIfNecessary(Object input) {
			if (input instanceof OriginalMessageHolder) {
				input = ((OriginalMessageHolder) input).getValue();
			}
			return input;
		}

		/**
		 * This operation will parse value coming in as Tuples to Object[].
		 */
		private Object[] parseMultipleValueArguments(Object multipleValueArgument, int argumentCount) {
			Object[] parsedArgumentValues = new Object[argumentCount];
			if (multipleValueArgument.getClass().getName().startsWith("reactor.util.function.Tuple")) {
				for (int i = 0; i < argumentCount; i++) {
					Expression parsed = new SpelExpressionParser().parseExpression("getT" + (i + 1) + "()");
					Object outputArgument = parsed.getValue(multipleValueArgument);
					parsedArgumentValues[i] = outputArgument;
				}
				return parsedArgumentValues;
			}
			throw new UnsupportedOperationException("At the moment only Tuple-based function are supporting multiple arguments");
		}

		@SuppressWarnings("unchecked")
		private boolean isInputConversionNecessary(Object input, Type type) {
			if (type == null || this.getRawClassFor(type) == Void.class || this.target instanceof RoutingFunction || this.isComposed() || this.target instanceof PassThruFunction) {
				if (this.getRawClassFor(type) == Void.class) {
					if (input instanceof Message) {
						input = ((Message) input).getPayload();
						if (input instanceof Optional) {
							input = ((Optional) input).orElseGet(() -> null);
						}
					}
					Assert.isNull(input, "Can't have non-null input with Void input type.");
				}
				return false;
			}
			return true;
		}
		/*
		 *
		 */
		private Object convertInputIfNecessary(Object input, Type type) {
			if (!this.isInputConversionNecessary(input, type)) {
				return input;
			}

			Object convertedInput = null;
			if (input instanceof Publisher) {
				convertedInput = this.convertInputPublisherIfNecessary((Publisher) input, type);
			}
			else if (FunctionTypeUtils.isMultipleArgumentType(type)) {
				Type[] inputTypes = ((ParameterizedType) type).getActualTypeArguments();
				Object[] multipleValueArguments = this.parseMultipleValueArguments(input, inputTypes.length);
				Object[] convertedInputs = new Object[inputTypes.length];
				for (int i = 0; i < multipleValueArguments.length; i++) {
					Object cInput = this.convertInputIfNecessary(multipleValueArguments[i], inputTypes[i]);
					convertedInputs[i] = cInput;
				}
				convertedInput = Tuples.fromArray(convertedInputs);
			}
			else if (this.skipInputConversion) {
				convertedInput = this.isInputTypeMessage()
						? input
						: new OriginalMessageHolder(((Message) input).getPayload(), (Message<?>) input);
			}
			else if (input instanceof Message) {
				input = this.filterOutHeaders((Message) input);
				if (((Message) input).getPayload().getClass().getName().equals("org.springframework.kafka.support.KafkaNull")) {
					return input;
				}

				if (functionInvocationHelper != null) {
					input = functionInvocationHelper.preProcessInput((Message<?>) input, messageConverter);
				}

				convertedInput = this.convertInputMessageIfNecessary((Message) input, type);
				if (convertedInput == null) { // give ConversionService a chance
					convertedInput = this.convertNonMessageInputIfNecessary(type, ((Message) input).getPayload(), false);
				}
				if (convertedInput != null && !FunctionTypeUtils.isMultipleArgumentType(this.inputType)) {
					convertedInput = !convertedInput.equals(input)
							? new OriginalMessageHolder(convertedInput, (Message<?>) input)
							: convertedInput;
				}
				if (convertedInput != null && logger.isDebugEnabled()) {
					logger.debug("Converted Message: " + input + " to: " +
							(convertedInput instanceof OriginalMessageHolder ? ((OriginalMessageHolder) convertedInput).value.getClass() : convertedInput));
				}
			}
			else {
				convertedInput = this.convertNonMessageInputIfNecessary(type, input, JsonMapper.isJsonString(input));
				if (convertedInput != null && logger.isDebugEnabled()) {
					logger.debug("Converted input: " + input + " to: " + convertedInput);
				}
			}
			// wrap in Message if necessary
			if (this.isWrapConvertedInputInMessage(convertedInput)) {
				convertedInput = MessageBuilder.withPayload(convertedInput).build();
			}

			Object finalInput = input;
			Assert.notNull(convertedInput, () -> "Failed to convert input: " + finalInput + " to " + type);
			return convertedInput;
		}

		// TODO temporary fix for https://github.com/spring-cloud/spring-cloud-stream/issues/2178
		// need a cleaner solution
		@SuppressWarnings("unchecked")
		private Message filterOutHeaders(Message message) {
			return MessageBuilder.fromMessage(message).removeHeader("spring.cloud.stream.sendto.destination").build();
		}

		private boolean isExtractPayload(Message<?> message, Type type) {
			if (this.propagateInputHeaders) {
				return false;
			}
			if (this.isRoutingFunction()) {
				return false;
			}
			if (FunctionTypeUtils.isCollectionOfMessage(type)) {
				return true;
			}
			if (FunctionTypeUtils.isMessage(type)) {
				return false;
			}

			Object payload = message.getPayload();
			if ((payload instanceof byte[])) {
				return false;
			}
			if (ObjectUtils.isArray(payload)) {
				payload = CollectionUtils.arrayToList(payload);
			}
			if (payload instanceof Collection && !CollectionUtils.isEmpty((Collection<?>) payload)
					&& Message.class.isAssignableFrom(CollectionUtils.findCommonElementType((Collection<?>) payload))) {
				return true;
			}
			if (this.containsRetainMessageSignalInHeaders(message)) {
				return false;
			}
			return true;
		}

		/**
		 * This is an optional conversion which would only happen if `expected-content-type` is
		 * set as a header in a message or explicitly provided as part of the lookup.
		 */
		@SuppressWarnings("unchecked")
		private Object convertOutputIfNecessary(Object output, Type type, String[] contentType) {
			Object convertedOutput = output;
			if (this.skipOutputConversion) {
				return convertedOutput;
			}

			if (convertedOutput instanceof Publisher) {
				return this.convertOutputPublisherIfNecessary((Publisher) convertedOutput, type, contentType);
			}

			if (convertedOutput instanceof Message) {
				if (((Message) convertedOutput).getPayload() instanceof byte[] && ObjectUtils.isEmpty(contentType)) {
					return convertedOutput;
				}
				else if (isExtractPayload((Message<?>) convertedOutput, type)) {
					convertedOutput = ((Message) convertedOutput).getPayload();
				}
			}

			if (this.enhancer != null) {
				convertedOutput = enhancer.apply(convertedOutput);
			}
			if (this.getTarget() instanceof PassThruFunction) { // scst-2303
				Message enrichedMessage;
				if (convertedOutput instanceof Message) {
					enrichedMessage = MessageBuilder.fromMessage((Message) convertedOutput)
						.setHeader(MessageHeaders.CONTENT_TYPE, contentType[0]).build();
				}
				else {
					enrichedMessage = MessageBuilder.withPayload(convertedOutput)
						.setHeader(MessageHeaders.CONTENT_TYPE, contentType[0]).build();
				}
				return messageConverter.toMessage(enrichedMessage.getPayload(), enrichedMessage.getHeaders());
			}

			if (ObjectUtils.isEmpty(contentType)) {
				return convertedOutput;
			}


			if (FunctionTypeUtils.isMultipleArgumentType(type)) {
				convertedOutput = this.convertMultipleOutputArgumentTypeIfNecesary(convertedOutput, type, contentType);
			}
			else if (convertedOutput instanceof Message) {
				convertedOutput = this.convertOutputMessageIfNecessary(convertedOutput, ObjectUtils.isEmpty(contentType) ? null : contentType[0]);
			}
			else if (convertedOutput instanceof Collection && this.isOutputTypeMessage()) {
				convertedOutput = this.convertMultipleOutputValuesIfNecessary(convertedOutput, ObjectUtils.isEmpty(contentType) ? null : contentType);
			}
			else if (ObjectUtils.isArray(convertedOutput) && !(convertedOutput instanceof byte[])) {
				convertedOutput = this.convertMultipleOutputValuesIfNecessary(convertedOutput, ObjectUtils.isEmpty(contentType) ? null : contentType);
			}
			else {
				convertedOutput = messageConverter.toMessage(convertedOutput,
						new MessageHeaders(Collections.singletonMap(MessageHeaders.CONTENT_TYPE, contentType == null ? "application/json" : contentType[0])));
			}

			return convertedOutput;
		}

		/**
		 * Will check if message contains any of the headers that are considered to serve as
		 * signals to retain output as Message (regardless of the output type of function).
		 * At this moment presence of 'scf-func-name' header or any header that begins with `lambda'
		 * (use by AWS) will result in this method returning true.
		 */
		/*
		 * TODO we need to investigate if this could be extracted into some type of strategy since at
		 * the pure core level there is no case for this to ever be true. In fact today it is only AWS Lambda
		 * case that requires it since it may contain forwarding url
		 */
		private boolean containsRetainMessageSignalInHeaders(Message message) {
			if (functionInvocationHelper != null && functionInvocationHelper.isRetainOuputAsMessage(message)) {
				return true;
			}
			else {
				for (String headerName : message.getHeaders().keySet()) {
					if (headerName.startsWith("lambda") ||
						headerName.startsWith("scf-func-name")) {
						return true;
					}
				}
				return false;
			}
		}

		/*
		 *
		 */
		private Object convertNonMessageInputIfNecessary(Type inputType, Object input, boolean maybeJson) {
			Object convertedInput = null;
			Class<?> rawInputType = this.isTypePublisher(inputType) || this.isInputTypeMessage()
					? FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(inputType))
					: this.getRawClassFor(inputType);

			if (maybeJson && !Message.class.isAssignableFrom(rawInputType)) {
				if (FunctionTypeUtils.isMessage(inputType)) {
					inputType = FunctionTypeUtils.getGenericType(inputType);
				}
				if (Object.class != inputType) {
					convertedInput = SimpleFunctionRegistry.this.jsonMapper.fromJson(input, inputType);
				}
			}
			else if (SimpleFunctionRegistry.this.conversionService != null
					&& !rawInputType.equals(input.getClass())
					&& SimpleFunctionRegistry.this.conversionService.canConvert(input.getClass(), rawInputType)) {
				convertedInput = SimpleFunctionRegistry.this.conversionService.convert(input, rawInputType);
			}
			if (convertedInput == null && logger.isDebugEnabled()) {
				logger.debug("Failed to convert input '" + input + "' to type " + inputType + ". Will use it as is.");
			}
			return convertedInput == null ? input : convertedInput;
		}

		/*
		 *
		 */
		private boolean isWrapConvertedInputInMessage(Object convertedInput) {
			return this.inputType != null
					&& FunctionTypeUtils.isMessage(this.inputType)
					&& !(convertedInput instanceof Message)
					&& !(convertedInput instanceof Publisher)
					&& !(convertedInput instanceof OriginalMessageHolder);
		}

		/*
		 *
		 */
		private Type extractActualValueTypeIfNecessary(Type type) {
			if (type  instanceof ParameterizedType && (FunctionTypeUtils.isPublisher(type) || FunctionTypeUtils.isMessage(type))) {
				return FunctionTypeUtils.getGenericType(type);
			}
			return type;
		}

		/*
		 *
		 */
		private Object convertInputMessageIfNecessary(Message message, Type type) {
			if (type == null) {
				return null;
			}
			if (message.getPayload() instanceof Optional) {
				return message;
			}
			if (message.getPayload() instanceof Collection<?>) {
				Type itemType = FunctionTypeUtils.getImmediateGenericType(type, 0);
				if (itemType == null) {
					itemType = type;
				}
				Type collectionType = CollectionUtils.findCommonElementType((Collection<?>) message.getPayload());
				if (collectionType == itemType) {
					return message.getPayload();
				}
			}

			Object convertedInput = message.getPayload();

			Type itemType = this.extractActualValueTypeIfNecessary(type);
			Class<?> rawType = FunctionTypeUtils.isMessage(type)
					? FunctionTypeUtils.getRawType(itemType)
					: FunctionTypeUtils.getRawType(type);
			convertedInput = type instanceof ParameterizedType
					? SimpleFunctionRegistry.this.messageConverter.fromMessage(message, rawType, itemType)
					: SimpleFunctionRegistry.this.messageConverter.fromMessage(message, rawType);

			if (convertedInput != null && !rawType.isAssignableFrom(convertedInput.getClass())) {
				logger.warn("Failed to convert input to " + rawType + ". Will attempt to invoke function with raw type");
			}

			if (FunctionTypeUtils.isMessage(type)) {
				if (convertedInput == null) {
					if (logger.isDebugEnabled()) {
						/*
						 * In the event conversion was unsuccessful we simply return the original un-converted message.
						 * This will help to deal with issues like KafkaNull and others. However if this was not the intention
						 * of the developer, this would be discovered early in the development process where the
						 * additional message converter could be added to facilitate the conversion.
						 */
						logger.debug("Input type conversion of payload " + message.getPayload() + " resulted in 'null'. "
								+ "Will use the original message as input.");
					}

					convertedInput = message;
				}
				else {
					if (!(convertedInput instanceof Message)) {
						convertedInput = MessageBuilder.withPayload(convertedInput).copyHeaders(message.getHeaders()).build();
					}
				}
			}
			return convertedInput;
		}

		/**
		 * This method handles function with multiple output arguments (e.g. Tuple2<..>)
		 */
		private Object convertMultipleOutputArgumentTypeIfNecesary(Object output, Type type, String[] contentType) {
			Type[] outputTypes = ((ParameterizedType) type).getActualTypeArguments();
			Object[] multipleValueArguments = this.parseMultipleValueArguments(output, outputTypes.length);
			Object[] convertedOutputs = new Object[outputTypes.length];
			for (int i = 0; i < multipleValueArguments.length; i++) {
				String[] ctToUse = !ObjectUtils.isEmpty(contentType)
						? new String[]{contentType[i]}
						: new String[] {"application/json"};
				Object convertedInput = this.convertOutputIfNecessary(multipleValueArguments[i], outputTypes[i], ctToUse);
				convertedOutputs[i] = convertedInput;
			}
			return Tuples.fromArray(convertedOutputs);
		}

		/*
		 *
		 */
		@SuppressWarnings("unchecked")
		private Object convertOutputMessageIfNecessary(Object output, String expectedOutputContetntType) {
			String contentType = ((Message) output).getHeaders().containsKey(FunctionProperties.EXPECT_CONTENT_TYPE_HEADER)
					? (String) ((Message) output).getHeaders().get(FunctionProperties.EXPECT_CONTENT_TYPE_HEADER)
							: expectedOutputContetntType;

			if (StringUtils.hasText(contentType)) {
				Map<String, Object> headersMap = new HashMap(((Message) output).getHeaders());
				String[] expectedContentTypes = StringUtils.delimitedListToStringArray(contentType, ",");
				for (String expectedContentType : expectedContentTypes) {
					headersMap.put(MessageHeaders.CONTENT_TYPE, expectedContentType);
					Message message = MessageBuilder.withPayload(((Message) output).getPayload()).copyHeaders(headersMap).build();
					Object result = messageConverter.toMessage(message.getPayload(), message.getHeaders());
					if (result != null) {
						return result;
					}
				}
			}
			return output;
		}

		/**
		 * This one is used to convert individual value of Collection or array.
		 */
		@SuppressWarnings("unchecked")
		private Object convertMultipleOutputValuesIfNecessary(Object output, String[] contentType) {
			Collection outputCollection = ObjectUtils.isArray(output) ? CollectionUtils.arrayToList(output) : (Collection) output;
			Collection convertedOutputCollection = outputCollection instanceof List ? new ArrayList<>() : new TreeSet<>();
			Type type = this.isOutputTypeMessage() ? FunctionTypeUtils.getGenericType(this.outputType) : this.outputType;
			for (Object outToConvert : outputCollection) {
				Object result = this.convertOutputIfNecessary(outToConvert, type, contentType);
				Assert.notNull(result, () -> "Failed to convert output '" + outToConvert + "'");
				convertedOutputCollection.add(result);
			}
			return ObjectUtils.isArray(output) ? convertedOutputCollection.toArray() : convertedOutputCollection;
		}

		/*
		 *
		 */
		@SuppressWarnings("unchecked")
		private Object convertInputPublisherIfNecessary(Publisher publisher, Type type) {
			if (FunctionTypeUtils.isMono(type) && publisher instanceof Flux) {
				publisher = Mono.from(publisher);
			}
			else if (FunctionTypeUtils.isFlux(type) && publisher instanceof Mono) {
				publisher = Flux.from(publisher);
			}
			Type actualType = type != null && FunctionTypeUtils.isPublisher(type)
					? FunctionTypeUtils.getImmediateGenericType(type, 0)
					: type;
			return publisher instanceof Mono
					? Mono.from(publisher).map(v -> {
						try {
							return this.convertInputIfNecessary(v, actualType == null ? type : actualType);
						}
						catch (Exception e) {
							throw new IllegalStateException("Failed to convert input", e);
						}
					})
					: Flux.from(publisher).map(v -> {
						try {
							return this.convertInputIfNecessary(v, actualType == null ? type : actualType);
						}
						catch (Exception e) {
							throw new IllegalStateException("Failed to convert input", e);
						}
					});
		}

		/*
		 *
		 */
		@SuppressWarnings("unchecked")
		private Object convertOutputPublisherIfNecessary(Publisher publisher, Type type, String[] expectedOutputContentType) {
			return publisher instanceof Mono
					? Mono.from(publisher).map(v -> {
						try {
							return this.convertOutputIfNecessary(v, type, expectedOutputContentType);
						}
						// SmartB Modification
						// FIX force message conversion error propagation
						catch (ResponseStatusException e) {
							throw e;
						}
						// SmartB End Of Modification
						catch (Exception e) {
							throw new IllegalStateException("Failed to convert output", e);
						}
					})
					: Flux.from(publisher).map(v -> {
						try {
							return this.convertOutputIfNecessary(v, type, expectedOutputContentType);
						}
						// SmartB Modification
						// FIX force message conversion error propagation
						catch (ResponseStatusException e) {
							throw e;
						}
						// SmartB End Of Modification
						catch (Exception e) {
							throw new IllegalStateException("Failed to convert output", e);
						}
					});
		}
	}

	/**
	 *
	 */
	private static final class OriginalMessageHolder  {
		private final Object value;

		private final Message<?> originalMessage;

		private OriginalMessageHolder(Object value, Message<?> originalMessage) {
			this.value = value;
			this.originalMessage = originalMessage;
		}

		public Object getValue() {
			return this.value;
		}

		public Message<?> getOriginalMessage() {
			return this.originalMessage;
		}
	}

	public static class PassThruFunction implements Function<Object, Object> {
		@Override
		public Object apply(Object t) {
			return t;
		}
	}
}
