/*
 * Copyright 2019-present the original author or authors.
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

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.config.FunctionContextUtils;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.KotlinDetector;
import org.springframework.core.ResolvableType;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Set of utility operations to interrogate function definitions.
 *
 * @author Oleg Zhurakousky
 * @author Andrey Shlykov
 * @author Artem Bilan
 * @since 3.0
 */
public final class FunctionTypeUtils {

	private static Log logger = LogFactory.getLog(FunctionTypeUtils.class);

	private static Type ROUTING_FUNCTION_TYPE = discoverFunctionTypeFromClass(RoutingFunction.class);

	private FunctionTypeUtils() {

	}

	public static Type functionType(Type input, Type output) {
		return ResolvableType
			.forClassWithGenerics(Function.class, ResolvableType.forType(input), ResolvableType.forType(output))
			.getType();
	}

	public static Type consumerType(Type input) {
		return ResolvableType.forClassWithGenerics(Consumer.class, ResolvableType.forType(input)).getType();
	}

	public static Type supplierType(Type output) {
		return ResolvableType.forClassWithGenerics(Supplier.class, ResolvableType.forType(output)).getType();
	}

	/**
	 * Will return 'true' if the provided type is a {@link Collection} type. This also
	 * includes collections wrapped in {@link Message}. For example, If provided type is
	 * {@code Message<List<Foo>>} this operation will return 'true'.
	 * @param type type to interrogate
	 * @return 'true' if this type represents a {@link Collection}. Otherwise 'false'.
	 */
	public static boolean isTypeCollection(Type type) {
		Class rawClass = getRawType(type);
		if (rawClass == null) {
			return false;
		}
		if (Collection.class.isAssignableFrom(getRawType(type))) {
			return true;
		}
		type = getGenericType(type);
		type = type == null ? Object.class : type;
		Class<?> rawType = type instanceof ParameterizedType ? getRawType(type) : (Class<?>) type;
		return Collection.class.isAssignableFrom(rawType) || JsonNode.class.isAssignableFrom(rawType);
	}

	public static boolean isTypeMap(Type type) {
		if (Map.class.isAssignableFrom(getRawType(type))) {
			return true;
		}
		type = getGenericType(type);
		Class<?> rawType = type instanceof ParameterizedType ? getRawType(type) : (Class<?>) type;
		return Map.class.isAssignableFrom(rawType);
	}

	public static boolean isTypeArray(Type type) {
		return type instanceof GenericArrayType;
	}

	public static boolean isJsonNode(Type type) {
		return getRawType(type).isArray();
	}

	/**
	 * A convenience method identical to {@link #getImmediateGenericType(Type, int)} for
	 * cases when provided 'type' is {@link Publisher} or {@link Message}.
	 * @param type type to interrogate
	 * @return generic type if possible otherwise the same type as provided
	 */
	public static Type getGenericType(Type type) {
		if (isPublisher(type) || isMessage(type)) {
			type = getImmediateGenericType(type, 0);
		}

		if (type instanceof WildcardType) {
			type = Object.class;
		}
		return type;
	}

	/**
	 * Effectively converts {@link Type} which could be {@link ParameterizedType} to raw
	 * Class (no generics).
	 * @param type actual {@link Type} instance
	 * @return instance of {@link Class} as raw representation of the provided
	 * {@link Type}
	 */
	public static Class<?> getRawType(Type type) {
		if (type instanceof WildcardType) {
			Type[] upperbounds = ((WildcardType) type).getUpperBounds();
			/*
			 * Kotlin may have something like this <? extends Message> which is
			 * technically a whildcard yet it has upper/lower types. See GH-1260
			 */
			return ObjectUtils.isEmpty(upperbounds) ? Object.class : getRawType(upperbounds[0]);
		}
		return ResolvableType.forType(type).getRawClass() == null ? Object.class
				: ResolvableType.forType(type).getRawClass();
	}

	/**
	 * Will attempt to discover functional methods on the class. It's applicable for POJOs
	 * as well as functional classes in `java.util.function` package. For the later the
	 * names of the methods are well known (`apply`, `accept` and `get`). For the former
	 * it will attempt to discover a single method following semantics described in (see
	 * {@link FunctionalInterface})
	 * @param pojoFunctionClass the class to introspect
	 * @return functional method
	 */
	public static Method discoverFunctionalMethod(Class<?> pojoFunctionClass) {
		if (Supplier.class.isAssignableFrom(pojoFunctionClass)) {
			return Stream.of(ReflectionUtils.getAllDeclaredMethods(pojoFunctionClass))
				.filter(m -> !m.isSynthetic() && m.getName().equals("get"))
				.findFirst()
				.get();
		}
		else if (Consumer.class.isAssignableFrom(pojoFunctionClass)
				|| BiConsumer.class.isAssignableFrom(pojoFunctionClass)) {
			return Stream.of(ReflectionUtils.getAllDeclaredMethods(pojoFunctionClass))
				.filter(m -> !m.isSynthetic() && m.getName().equals("accept"))
				.findFirst()
				.get();
		}
		else if (Function.class.isAssignableFrom(pojoFunctionClass)
				|| BiFunction.class.isAssignableFrom(pojoFunctionClass)) {
			return Stream.of(ReflectionUtils.getAllDeclaredMethods(pojoFunctionClass))
				.filter(m -> !m.isSynthetic() && m.getName().equals("apply"))
				.findFirst()
				.get();
		}

		List<Method> methods = new ArrayList<>();
		ReflectionUtils.doWithMethods(pojoFunctionClass, method -> {
			if (method.getDeclaringClass() == pojoFunctionClass) {
				methods.add(method);
			}

		}, method -> !method.getDeclaringClass().isAssignableFrom(Object.class) && !method.isSynthetic()
				&& !method.isBridge() && !method.isVarArgs());

		if (methods.size() > 1) {
			for (Method candidadteMethod : methods) {
				if (candidadteMethod.getName().equals("apply") || candidadteMethod.getName().equals("accept")
						|| candidadteMethod.getName().equals("get") || candidadteMethod.getName().equals("invoke")) {
					return candidadteMethod;
				}
			}
		}
		return CollectionUtils.isEmpty(methods) ? null : methods.get(0);
	}

	public static Type discoverFunctionTypeFromType(Type functionalType) {
		Type typeToReturn = null;
		ResolvableType functionType;
		if (Function.class.isAssignableFrom(getRawType(functionalType))) {
			functionType = ResolvableType.forType(functionalType).as(Function.class);
		}
		else if (Consumer.class.isAssignableFrom(getRawType(functionalType))) {
			functionType = ResolvableType.forType(functionalType).as(Consumer.class);
		}
		else {
			functionType = ResolvableType.forType(functionalType).as(Supplier.class);
		}
		typeToReturn = resolveType(functionType);
		return typeToReturn;
	}

	public static Type discoverFunctionTypeFromClass(Class<?> functionalClass) {
		if (KotlinDetector.isKotlinPresent()) {
			if (Function1.class.isAssignableFrom(functionalClass)) {
				ResolvableType kotlinType = ResolvableType.forClass(functionalClass).as(Function1.class);
				return GenericTypeResolver.resolveType(kotlinType.getType(), functionalClass);
			}
			else if (Function0.class.isAssignableFrom(functionalClass)) {
				ResolvableType kotlinType = ResolvableType.forClass(functionalClass).as(Function0.class);
				return GenericTypeResolver.resolveType(kotlinType.getType(), functionalClass);
			}
		}
		return discoverFunctionTypeFromType(functionalClass);
	}

	/**
	 * Discovers the function {@link Type} based on the signature of a factory method. For
	 * example, given the following method
	 * {@code Function<Message<Person>, Message<String>> uppercase()} of class Foo -
	 * {@code Type type = discoverFunctionTypeFromFunctionFactoryMethod(Foo.class, "uppercase");}
	 * @param clazz instance of Class containing the factory method
	 * @param methodName factory method name
	 * @return type of the function
	 */
	public static Type discoverFunctionTypeFromFunctionFactoryMethod(Class<?> clazz, String methodName) {
		return discoverFunctionTypeFromFunctionFactoryMethod(ReflectionUtils.findMethod(clazz, methodName));
	}

	/**
	 * Discovers the function {@link Type} based on the signature of a factory method. For
	 * example, given the following method
	 * {@code Function<Message<Person>, Message<String>> uppercase()} of class Foo -
	 * {@code Type type = discoverFunctionTypeFromFunctionFactoryMethod(Foo.class, "uppercase");}
	 * @param method factory method
	 * @return type of the function
	 */
	public static Type discoverFunctionTypeFromFunctionFactoryMethod(Method method) {
		return method.getGenericReturnType();
	}

	/**
	 * Unlike {@link #discoverFunctionTypeFromFunctionFactoryMethod(Class, String)}, this
	 * method discovers function type from the well known method of Function(apply),
	 * Supplier(get) or Consumer(accept).
	 * @param functionMethod functional method
	 * @return type of the function
	 */
	public static Type discoverFunctionTypeFromFunctionMethod(Method functionMethod) {
		if (functionMethod == null) {
			return null;
		}
		Assert.isTrue(
				functionMethod.getName().equals("apply") || functionMethod.getName().equals("accept")
						|| functionMethod.getName().equals("get") || functionMethod.getName().equals("invoke"),
				"Only Supplier, Function or Consumer supported at the moment. Was "
						+ functionMethod.getDeclaringClass());

		ResolvableType functionType;
		if (functionMethod.getName().equals("apply") || functionMethod.getName().equals("invoke")) {
			ResolvableType input = ResolvableType.forMethodParameter(functionMethod, 0);
			if (input.getType() instanceof TypeVariable) {
				input = ResolvableType.forClass(Object.class);
			}
			ResolvableType output = ResolvableType.forMethodReturnType(functionMethod);
			if (output.getType() instanceof TypeVariable) {
				output = ResolvableType.forClass(Object.class);
			}
			functionType = ResolvableType.forClassWithGenerics(Function.class, input, output);
		}
		else if (functionMethod.getName().equals("accept")) {
			ResolvableType parameterType = ResolvableType.forMethodParameter(functionMethod, 0);
			if (parameterType.getType() instanceof TypeVariable) {
				parameterType = ResolvableType.forClass(Object.class);
			}
			functionType = ResolvableType.forClassWithGenerics(Consumer.class, parameterType);
		}
		else {
			ResolvableType returnType = ResolvableType.forMethodReturnType(functionMethod);
			if (returnType.getType() instanceof TypeVariable) {
				returnType = ResolvableType.forClass(Object.class);
			}
			functionType = ResolvableType.forClassWithGenerics(Supplier.class, returnType);
		}
		return functionType.getType();
	}

	public static int getInputCount(FunctionInvocationWrapper function) {
		int inputCount = function.isSupplier() ? 0 : 1;
		if (inputCount > 0) {
			Type inputType = function.getInputType();
			if (isMulti(inputType)) {
				inputCount = ((ParameterizedType) inputType).getActualTypeArguments().length;
			}
		}
		return inputCount;
	}

	public static int getOutputCount(FunctionInvocationWrapper function) {
		int outputCount = function.isConsumer() ? 0 : 1;
		if (outputCount > 0) {
			Type outputType = function.getOutputType();
			if (isMulti(outputType)) {
				outputCount = ((ParameterizedType) outputType).getActualTypeArguments().length;
			}
		}
		return outputCount;
	}

	/**
	 * In the event the input type is {@link ParameterizedType} this method returns its
	 * generic type.
	 * @param functionType instance of function type
	 * @return generic type or input type
	 */
	public static Type getComponentTypeOfInputType(Type functionType) {
		Type inputType = getInputType(functionType);
		return getImmediateGenericType(inputType, 0);
	}

	/**
	 * In the event the output type is {@link ParameterizedType} this method returns its
	 * generic type.
	 * @param functionType instance of function type
	 * @return generic type or output type
	 */
	public static Type getComponentTypeOfOutputType(Type functionType) {
		Type outputType = getOutputType(functionType);
		return getImmediateGenericType(outputType, 0);
	}

	/**
	 * Will resolve @{@link ResolvableType} to {@link Type} preserving all the resolved
	 * generics.
	 * @param typeWithGenerics - instance of {@link ResolvableType}.
	 * @return - {@link Type} representation of the provided {@link ResolvableType}.
	 */
	public static Type resolveType(ResolvableType typeWithGenerics) {
		if (typeWithGenerics.hasResolvableGenerics()) {
			ResolvableType[] generics = typeWithGenerics.getGenerics();
			List<ResolvableType> resolvedGenerics = new ArrayList<>();
			for (int i = 0; i < generics.length; i++) {
				ResolvableType genericType = typeWithGenerics.getGenerics()[i];
				resolvedGenerics.add(ResolvableType.forType(resolveType(genericType)));
			}
			return ResolvableType
				.forClassWithGenerics(typeWithGenerics.getRawClass(), resolvedGenerics.toArray(new ResolvableType[0]))
				.getType();
		}
		else {
			return typeWithGenerics.resolve();
		}
	}

	public static Type getOutputType(Type functionType) {
		assertSupportedTypes(functionType);
		if (isConsumer(functionType)) {
			logger.debug("Consumer does not have output type, returning null as output type.");
			return null;
		}

		if (KotlinDetector.isKotlinPresent() && Function1.class.isAssignableFrom(getRawType(functionType))) { // Kotlin
			return ResolvableType.forType(getImmediateGenericType(functionType, 1)).getType();
		}
		else {
			ResolvableType resolvableFunctionType = isSupplier(functionType)
					? ResolvableType.forType(functionType).as(Supplier.class)
					: ResolvableType.forType(functionType).as(Function.class);
			ResolvableType generics = isSupplier(functionType) ? resolvableFunctionType.getGenerics()[0]
					: resolvableFunctionType.getGenerics()[1];
			Type outputType = FunctionTypeUtils.resolveType(generics);
			return outputType == null || outputType instanceof TypeVariable<?> ? Object.class : outputType;
		}
	}

	/**
	 * Returns input type of function type that represents Function or Consumer.
	 * @param functionType the Type of Function or Consumer
	 * @return the input type as {@link Type}
	 */
	public static Type getInputType(Type functionType) {
		assertSupportedTypes(functionType);
		if (isSupplier(functionType)) {
			logger.debug("Supplier does not have input type, returning null as input type.");
			return null;
		}

		if (KotlinDetector.isKotlinPresent() && Function1.class.isAssignableFrom(getRawType(functionType))) { // Kotlin
			return ResolvableType.forType(getImmediateGenericType(functionType, 1)).getType();
		}
		else {
			ResolvableType resolvableFunctionType = isConsumer(functionType)
					? ResolvableType.forType(functionType).as(Consumer.class)
					: ResolvableType.forType(functionType).as(Function.class);
			ResolvableType generics = resolvableFunctionType.getGenerics()[0];
			Type inputType = FunctionTypeUtils.resolveType(generics);
			return inputType == null || inputType instanceof TypeVariable<?> ? Object.class : inputType;
		}
	}

	@SuppressWarnings("rawtypes")
	public static Type discoverFunctionType(Object function, String functionName,
			GenericApplicationContext applicationContext) {
		if (function instanceof RoutingFunction) {
			return ROUTING_FUNCTION_TYPE;
		}
		else if (function instanceof FunctionRegistration) {
			return ((FunctionRegistration) function).getType();
		}
		if (applicationContext.containsBean(functionName + FunctionRegistration.REGISTRATION_NAME_SUFFIX)) {
			// for Kotlin primarily
			FunctionRegistration fr = applicationContext
				.getBean(functionName + FunctionRegistration.REGISTRATION_NAME_SUFFIX, FunctionRegistration.class);
			return fr.getType();
		}

		functionName = discoverBeanDefinitionNameByQualifier(applicationContext.getBeanFactory(), functionName);
		Type type = FunctionContextUtils.findType(applicationContext.getBeanFactory(), functionName);
		if (type == null || type instanceof Class) {
			boolean beanDefinitionExists = false;
			String functionBeanDefinitionName = discoverDefinitionName(functionName, applicationContext);
			beanDefinitionExists = applicationContext.getBeanFactory()
				.containsBeanDefinition(functionBeanDefinitionName);
			if (applicationContext.containsBean("&" + functionName)) {
				Class<?> objectType = applicationContext.getBean("&" + functionName, FactoryBean.class).getObjectType();
				return FunctionTypeUtils.discoverFunctionTypeFromClass(objectType);
			}

			type = FunctionTypeUtils.discoverFunctionTypeFromClass(function.getClass());
			if (beanDefinitionExists) {
				Type t = FunctionTypeUtils.getImmediateGenericType(type, 0);
				if (t == null || t == Object.class) {
					type = FunctionContextUtils.findType(applicationContext.getBeanFactory(),
							functionBeanDefinitionName);
				}
			}
			else if (!(type instanceof ParameterizedType)) {
				String beanDefinitionName = discoverBeanDefinitionNameByQualifier(applicationContext.getBeanFactory(),
						functionName);
				if (StringUtils.hasText(beanDefinitionName)) {
					type = FunctionContextUtils.findType(applicationContext.getBeanFactory(), beanDefinitionName);
				}
			}
		}
		else if (type instanceof ParameterizedType) {
			ResolvableType resolvableType = ResolvableType.forType(type);
			if (FactoryBean.class.isAssignableFrom(resolvableType.toClass())) {
				return resolvableType.getGeneric(0).getType();
			}
		}
		return type;
	}

	public static String discoverBeanDefinitionNameByQualifier(ListableBeanFactory beanFactory, String qualifier) {
		String[] candidateBeans = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, Object.class);

		for (String beanName : candidateBeans) {
			if (BeanFactoryAnnotationUtils.isQualifierMatch(qualifier::equals, beanName, beanFactory)) {
				return beanName;
			}
		}
		return null;
	}

	public static Type getImmediateGenericType(Type type, int index) {
		if (type instanceof ParameterizedType) {
			return ((ParameterizedType) type).getActualTypeArguments()[index];
		}
		return null;
	}

	public static boolean isPublisher(Type type) {
		return isFlux(type) || isMono(type);
	}

	public static boolean isFlux(Type type) {
		return getRawType(type) == Flux.class;
	}

	public static boolean isCollectionOfMessage(Type type) {
		if (isMessage(type) && (isTypeCollection(type) || isTypeArray(type))) {
			if (isTypeCollection(type)) {
				return isMessage(getImmediateGenericType(type, 0));
			}
			else if (type instanceof GenericArrayType arrayType) {
				return true;
			}
		}
		return false;
	}

	public static boolean isMessage(Type type) {
		if (isPublisher(type)) {
			type = getImmediateGenericType(type, 0);
		}
		if (type instanceof GenericArrayType arrayType) {
			type = arrayType.getGenericComponentType();
		}

		Class<?> resolveRawClass = FunctionTypeUtils.getRawType(type);
		if (type instanceof ParameterizedType && !Message.class.isAssignableFrom(resolveRawClass)) {
			type = getImmediateGenericType(type, 0);
		}
		resolveRawClass = FunctionTypeUtils.getRawType(type);
		if (resolveRawClass == null) {
			return false;
		}
		return Message.class.isAssignableFrom(resolveRawClass);
	}

	/**
	 * Determines if input argument to a Function is an array.
	 * @param functionType the function type
	 * @return true if input type is an array, otherwise false
	 */
	public static boolean isOutputArray(Type functionType) {
		Type outputType = FunctionTypeUtils.getOutputType(functionType);
		return outputType instanceof GenericArrayType
				|| outputType instanceof Class && ((Class<?>) outputType).isArray();
	}

	public static boolean isSupplier(Type type) {
		return isOfType(type, Supplier.class);
	}

	public static boolean isFunction(Type type) {
		return isOfType(type, Function.class);
	}

	public static boolean isConsumer(Type type) {
		return isOfType(type, Consumer.class);
	}

	public static boolean isMono(Type type) {
		type = extractReactiveType(type);
		return type == null ? false : type.getTypeName().startsWith("reactor.core.publisher.Mono");
	}

	public static boolean isMultipleArgumentType(Type type) {
		if (type != null) {
			if (ResolvableType.forType(type).isArray()) {
				return false;
			}
			Class<?> clazz = ResolvableType.forType(type).getRawClass();
			return clazz.getName().startsWith("reactor.util.function.Tuple");
		}
		return false;
	}

	static Type fromFunctionMethod(Method functionalMethod) {
		Type[] parameterTypes = functionalMethod.getGenericParameterTypes();

		Type functionType = null;
		switch (parameterTypes.length) {
			case 0:
				functionType = ResolvableType
					.forClassWithGenerics(Supplier.class, ResolvableType.forMethodReturnType(functionalMethod))
					.getType();
				break;
			case 1:
				if (Void.class.isAssignableFrom(functionalMethod.getReturnType())) {
					functionType = ResolvableType
						.forClassWithGenerics(Consumer.class, ResolvableType.forMethodParameter(functionalMethod, 0))
						.getType();
				}
				else {
					functionType = ResolvableType
						.forClassWithGenerics(Function.class, ResolvableType.forMethodParameter(functionalMethod, 0),
								ResolvableType.forMethodReturnType(functionalMethod))
						.getType();
				}
				break;
			default:
				throw new UnsupportedOperationException("Functional method: " + functionalMethod + " is not supported");
		}
		return functionType;
	}

	private static boolean isMulti(Type type) {
		return type.getTypeName().startsWith("reactor.util.function.Tuple");
	}

	private static boolean isOfType(Type type, Class<?> cls) {
		if (type instanceof Class) {
			return cls.isAssignableFrom((Class<?>) type);
		}
		else if (type instanceof ParameterizedType) {
			return isOfType(((ParameterizedType) type).getRawType(), cls);
		}
		return false;
	}

	private static void assertSupportedTypes(Type type) {
		if (type instanceof ParameterizedType) {
			type = ((ParameterizedType) type).getRawType();
			Assert.isTrue(type instanceof Class<?>,
					"Must be one of Supplier, Function, Consumer" + " or FunctionRegistration. Was " + type);
		}

		Class<?> candidateType = (Class<?>) type;

		Assert.isTrue(
				Supplier.class.isAssignableFrom(candidateType)
						|| (KotlinDetector.isKotlinPresent() && (Function0.class.isAssignableFrom(candidateType)
								|| Function1.class.isAssignableFrom(candidateType)))
						|| Function.class.isAssignableFrom(candidateType)
						|| Consumer.class.isAssignableFrom(candidateType)
						|| FunctionRegistration.class.isAssignableFrom(candidateType)
						|| IntConsumer.class.isAssignableFrom(candidateType)
						|| IntSupplier.class.isAssignableFrom(candidateType)
						|| IntFunction.class.isAssignableFrom(candidateType)
						|| ToIntFunction.class.isAssignableFrom(candidateType)
						|| LongConsumer.class.isAssignableFrom(candidateType)
						|| LongSupplier.class.isAssignableFrom(candidateType)
						|| LongFunction.class.isAssignableFrom(candidateType)
						|| ToLongFunction.class.isAssignableFrom(candidateType)
						|| DoubleConsumer.class.isAssignableFrom(candidateType)
						|| DoubleSupplier.class.isAssignableFrom(candidateType)
						|| DoubleFunction.class.isAssignableFrom(candidateType)
						|| ToDoubleFunction.class.isAssignableFrom(candidateType)
						|| type.getTypeName()
							.startsWith("org.springframework.context.annotation.ConfigurationClassEnhancer"),
				"Must be one of Supplier, Function, Consumer" + " or FunctionRegistration. Was " + type);
	}

	private static Type extractReactiveType(Type type) {
		if (type instanceof ParameterizedType
				&& FunctionRegistration.class.isAssignableFrom(((Class<?>) ((ParameterizedType) type).getRawType()))) {
			type = getImmediateGenericType(type, 0);
			if (type instanceof ParameterizedType) {
				type = getImmediateGenericType(type, 0);
			}
		}
		return type;
	}

	private static String discoverDefinitionName(String functionDefinition,
			GenericApplicationContext applicationContext) {
		String[] aliases = applicationContext.getAliases(functionDefinition);
		for (String alias : aliases) {
			if (applicationContext.getBeanFactory().containsBeanDefinition(alias)) {
				return alias;
			}
		}
		return functionDefinition;
	}

}
