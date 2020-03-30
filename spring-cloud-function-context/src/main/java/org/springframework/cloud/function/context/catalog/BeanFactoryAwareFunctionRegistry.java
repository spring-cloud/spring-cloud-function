/*
 * Copyright 2019-2020 the original author or authors.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
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
 * Implementation of {@link FunctionRegistry} and {@link FunctionCatalog} which is aware of the
 * underlying {@link BeanFactory} to access available functions. Functions that are registered via
 * {@link #register(FunctionRegistration)} operation are stored/cached locally.
 *
 * @author Oleg Zhurakousky
 * @author Eric Botard
 * @since 3.0
 */
public class BeanFactoryAwareFunctionRegistry
	implements FunctionRegistry, FunctionInspector, ApplicationContextAware, InitializingBean {

	private static Log logger = LogFactory.getLog(BeanFactoryAwareFunctionRegistry.class);

	/**
	 * Identifies MessageConversionExceptions that happen when input can't be converted.
	 */
	public static final String COULD_NOT_CONVERT_INPUT = "Could Not Convert Input";

	/**
	 * Identifies MessageConversionExceptions that happen when output can't be converted.
	 */
	public static final String COULD_NOT_CONVERT_OUTPUT = "Could Not Convert Output";

	private ConfigurableApplicationContext applicationContext;

	private final Map<Object, FunctionRegistration<Object>> registrationsByFunction = new HashMap<>();

	private final Map<String, FunctionRegistration<Object>> registrationsByName = new HashMap<>();

	private final ConversionService conversionService;

	private final CompositeMessageConverter messageConverter;

	private List<String> declaredFunctionDefinitions;

	public BeanFactoryAwareFunctionRegistry(ConversionService conversionService,
		@Nullable CompositeMessageConverter messageConverter) {
		this.conversionService = conversionService;
		this.messageConverter = messageConverter;

	}

	@Override
	public void afterPropertiesSet() throws Exception {
		String userDefinition = this.applicationContext.getEnvironment().getProperty("spring.cloud.function.definition");
		this.declaredFunctionDefinitions = StringUtils.hasText(userDefinition) ? Arrays.asList(userDefinition.split(";")) : Collections.emptyList();
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
		return this.applicationContext.getBeanNamesForType(Supplier.class).length +
			this.applicationContext.getBeanNamesForType(Function.class).length +
			this.applicationContext.getBeanNamesForType(Consumer.class).length;
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

	@SuppressWarnings("unchecked")
	@Override
	public Set<String> getNames(Class<?> type) {
		Set<String> registeredNames = registrationsByFunction.values().stream().flatMap(reg -> reg.getNames().stream())
			.collect(Collectors.toSet());
		if (type == null) {
			registeredNames
				.addAll(CollectionUtils.arrayToList(this.applicationContext.getBeanNamesForType(Function.class)));
			registeredNames
				.addAll(CollectionUtils.arrayToList(this.applicationContext.getBeanNamesForType(Supplier.class)));
			registeredNames
				.addAll(CollectionUtils.arrayToList(this.applicationContext.getBeanNamesForType(Consumer.class)));
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

	private Object locateFunction(String name) {
		Object function = this.registrationsByName.get(name);
		if (function == null && this.applicationContext.containsBean(name)) {
			function = this.applicationContext.getBean(name);
		}

		if (function != null && this.notFunction(function.getClass())
			&& this.applicationContext
			.containsBean(name + FunctionRegistration.REGISTRATION_NAME_SUFFIX)) { // e.g., Kotlin lambdas
			function = this.applicationContext
				.getBean(name + FunctionRegistration.REGISTRATION_NAME_SUFFIX, FunctionRegistration.class);
		}
		return function;
	}

	private boolean notFunction(Class<?> functionClass) {
		return !Function.class.isAssignableFrom(functionClass)
			&& !Supplier.class.isAssignableFrom(functionClass)
			&& !Consumer.class.isAssignableFrom(functionClass);
	}

	private Type discoverFunctionType(Object function, String... names) {
		if (function instanceof RoutingFunction) {
			return FunctionType.of(FunctionContextUtils.findType(applicationContext.getBeanFactory(), names)).getType();
		}
		boolean beanDefinitionExists = false;
		for (int i = 0; i < names.length && !beanDefinitionExists; i++) {
			beanDefinitionExists = this.applicationContext.getBeanFactory().containsBeanDefinition(names[i]);
			if (this.applicationContext.containsBean("&" + names[i])) {
				Class<?> objectType = this.applicationContext.getBean("&" + names[i], FactoryBean.class)
					.getObjectType();
				return FunctionTypeUtils.discoverFunctionTypeFromClass(objectType);
			}
		}
		if (!beanDefinitionExists) {
			logger.info("BeanDefinition for function name(s) '" + Arrays.asList(names) +
				"' can not be located. FunctionType will be based on " + function.getClass());
		}

		Type type = FunctionTypeUtils.discoverFunctionTypeFromClass(function.getClass());
		if (beanDefinitionExists) {
			Type t = FunctionTypeUtils.getImmediateGenericType(type, 0);
			if (t == null || t == Object.class) {
				type = FunctionType.of(FunctionContextUtils.findType(this.applicationContext.getBeanFactory(), names)).getType();
			}
		}
		return type;
	}

	private String discoverDefaultDefinitionIfNecessary(String definition) {
		if (StringUtils.isEmpty(definition)) {
			// the underscores are for Kotlin function registrations (see KotlinLambdaToFunctionAutoConfiguration)
			String[] functionNames = Stream.of(this.applicationContext.getBeanNamesForType(Function.class))
				.filter(n -> !n.endsWith(FunctionRegistration.REGISTRATION_NAME_SUFFIX) && !n
					.equals(RoutingFunction.FUNCTION_NAME)).toArray(String[]::new);
			String[] consumerNames = Stream.of(this.applicationContext.getBeanNamesForType(Consumer.class))
				.filter(n -> !n.endsWith(FunctionRegistration.REGISTRATION_NAME_SUFFIX) && !n
					.equals(RoutingFunction.FUNCTION_NAME)).toArray(String[]::new);
			String[] supplierNames = Stream.of(this.applicationContext.getBeanNamesForType(Supplier.class))
				.filter(n -> !n.endsWith(FunctionRegistration.REGISTRATION_NAME_SUFFIX) && !n
					.equals(RoutingFunction.FUNCTION_NAME)).toArray(String[]::new);

			/*
			 * we may need to add BiFunction and BiConsumer at some point
			 */
			List<String> names = Stream
				.concat(Stream.of(functionNames), Stream.concat(Stream.of(consumerNames), Stream.of(supplierNames)))
				.collect(Collectors.toList());

			if (!ObjectUtils.isEmpty(names)) {
				if (names.size() > 1) {
					logger.info("Found more then one function beans in BeanFactory: " + names
						+ ". If you did not intend to use functions, ignore this message. However, if you did "
						+ "intend to use functions in the context of spring-cloud-function, consider "
						+ "providing 'spring.cloud.function.definition' property pointing to a function bean(s) "
						+ "you intend to use. For example, 'spring.cloud.function.definition=myFunction'");
					return null;
				}
				definition = names.get(0);
			}
			else {
				if (this.registrationsByName.size() > 0) {
					Assert
						.isTrue(this.registrationsByName.size() == 1, "Found more then one function in local registry");
					definition = this.registrationsByName.keySet().iterator().next();
				}
			}

			if (StringUtils.hasText(definition) && this.applicationContext.containsBean(definition)) {
				Type functionType = discoverFunctionType(this.applicationContext.getBean(definition), definition);
				if (!FunctionTypeUtils.isSupplier(functionType) && !FunctionTypeUtils
					.isFunction(functionType) && !FunctionTypeUtils.isConsumer(functionType)) {
					logger
						.info("Discovered functional instance of bean '" + definition + "' as a default function, however its "
							+ "function argument types can not be determined. Discarding.");
					definition = null;
				}
			}
		}
		return definition;
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
					Type functionType = FunctionContextUtils.findType(applicationContext.getBeanFactory(), name);
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
	 */
	public class FunctionInvocationWrapper implements Function<Object, Object>, Consumer<Object>, Supplier<Object> {

		private final Object target;

		private final Type functionType;

		private final boolean composed;

		private final String[] acceptedOutputMimeTypes;

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

		@SuppressWarnings({"rawtypes", "unchecked"})
		private Object invokeFunction(Object input) {
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
			return invocationResult;
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
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
						? this.convertInputPublisherIfNecessary((Publisher<?>) inptArgument, FunctionTypeUtils
						.getInputType(functionType, i))
						: this
						.convertInputValueIfNecessary(inptArgument, FunctionTypeUtils.getInputType(functionType, i));
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
						if (FunctionTypeUtils.isMessage(type)) {
							convertedValue = MessageBuilder.withPayload(convertedValue)
								.copyHeaders(((Message<?>) value).getHeaders()).build();
						}
					}
					else if (!FunctionTypeUtils.isMessage(type)) {
						convertedValue = ((Message<?>) convertedValue).getPayload();
					}
				}
				else if (rawType instanceof Class<?>) { // see AWS adapter with WildardTypeImpl and Azure with Voids
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
			if (logger.isDebugEnabled()) {
				logger.debug("Converted input value " + convertedValue);
			}
			if (convertedValue == null) {
				throw new MessageConversionException(COULD_NOT_CONVERT_INPUT);
			}
			return convertedValue;
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
