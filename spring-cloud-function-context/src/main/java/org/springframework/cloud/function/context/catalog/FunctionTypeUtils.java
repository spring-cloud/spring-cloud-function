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

package org.springframework.cloud.function.context.catalog;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import net.jodah.typetools.TypeResolver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.config.FunctionContextUtils;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Set of utility operations to interrogate function definitions.
 *
 * @author Oleg Zhurakousky
 * @author Andrey Shlykov
 *
 * @since 3.0
 */
public final class FunctionTypeUtils {

	private static  Log logger = LogFactory.getLog(FunctionTypeUtils.class);

	private FunctionTypeUtils() {

	}

	/**
	 * Will return 'true' if the provided type is a {@link Collection} type.
	 * This also includes collections wrapped in {@link Message}. For example,
	 * If provided type is {@code Message<List<Foo>>} this operation will return 'true'.
	 *
	 * @param type type to interrogate
	 * @return 'true' if this type represents a {@link Collection}. Otherwise 'false'.
	 */
	public static boolean isTypeCollection(Type type) {
		if (Collection.class.isAssignableFrom(getRawType(type))) {
			return true;
		}
		type = getGenericType(type);
		Class<?> rawType = type instanceof ParameterizedType ? getRawType(type) : (Class<?>) type;
		return Collection.class.isAssignableFrom(rawType);
	}

	public static boolean isTypeArray(Type type) {
		return getRawType(type).isArray();
	}

	/**
	 * A convenience method identical to {@link #getImmediateGenericType(Type, int)}
	 * for cases when provided 'type' is {@link Publisher} or {@link Message}.
	 *
	 * @param type type to interrogate
	 * @return generic type if possible otherwise the same type as provided
	 */
	public static Type getGenericType(Type type) {
		if (isPublisher(type) || isMessage(type)) {
			type = getImmediateGenericType(type, 0);
		}

		return TypeResolver.reify(type instanceof GenericArrayType ? type : TypeResolver.reify(type));
	}

	/**
	 * Effectively converts {@link Type} which could be {@link ParameterizedType} to raw Class (no generics).
	 * @param type actual {@link Type} instance
	 * @return instance of {@link Class} as raw representation of the provided {@link Type}
	 */
	public static Class<?> getRawType(Type type) {
		return type != null ? TypeResolver
				.resolveRawClass(type instanceof GenericArrayType ? type : TypeResolver.reify(type), null) : null;
	}

	/**
	 * Will attempt to discover functional methods on the class. It's applicable for POJOs as well as
	 * functional classes in `java.util.function` package. For the later the names of the methods are
	 * well known (`apply`, `accept` and `get`). For the former it will attempt to discover a single method
	 * following semantics described in (see {@link FunctionalInterface})
	 *
	 * @param pojoFunctionClass the class to introspect
	 * @return functional method
	 */
	public static Method discoverFunctionalMethod(Class<?> pojoFunctionClass) {
		if (Supplier.class.isAssignableFrom(pojoFunctionClass)) {
			return Stream.of(ReflectionUtils.getDeclaredMethods(pojoFunctionClass)).filter(m -> !m.isSynthetic()
					&& m.getName().equals("get")).findFirst().get();
		}
		else if (Consumer.class.isAssignableFrom(pojoFunctionClass) || BiConsumer.class.isAssignableFrom(pojoFunctionClass)) {
			return Stream.of(ReflectionUtils.getDeclaredMethods(pojoFunctionClass)).filter(m -> !m.isSynthetic()
					&& m.getName().equals("accept")).findFirst().get();
		}
		else if (Function.class.isAssignableFrom(pojoFunctionClass) || BiFunction.class.isAssignableFrom(pojoFunctionClass)) {
			return Stream.of(ReflectionUtils.getDeclaredMethods(pojoFunctionClass)).filter(m -> !m.isSynthetic()
					&& m.getName().equals("apply")).findFirst().get();
		}

		List<Method> methods = new ArrayList<>();
		ReflectionUtils.doWithMethods(pojoFunctionClass, method -> {
			if (method.getDeclaringClass() == pojoFunctionClass) {
				methods.add(method);
			}

		}, method ->
			!method.getDeclaringClass().isAssignableFrom(Object.class)
			&& !method.isSynthetic() && !method.isBridge() && !method.isVarArgs());

		Assert.isTrue(methods.size() == 1, "Discovered " + methods.size() + " methods that would qualify as 'functional' - "
				+ methods + ".\n Class '" + pojoFunctionClass + "' is not a FunctionalInterface.");

		return methods.get(0);
	}

	@SuppressWarnings("unchecked")
	public static Type discoverFunctionTypeFromClass(Class<?> functionalClass) {
		Assert.isTrue(isFunctional(functionalClass), "Type must be one of Supplier, Function or Consumer");

		if (Function.class.isAssignableFrom(functionalClass)) {
			for (Type superInterface : functionalClass.getGenericInterfaces()) {
				if (superInterface != null && !superInterface.equals(Object.class)) {
					if (superInterface.toString().contains("KStream") && ResolvableType.forType(superInterface).getGeneric(1).isArray()) {
						return null;
					}
				}
			}
			return TypeResolver.reify(Function.class, (Class<Function<?, ?>>) functionalClass);
		}
		else if (Consumer.class.isAssignableFrom(functionalClass)) {
			return TypeResolver.reify(Consumer.class, (Class<Consumer<?>>) functionalClass);
		}
		else if (Supplier.class.isAssignableFrom(functionalClass)) {
			return TypeResolver.reify(Supplier.class, (Class<Supplier<?>>) functionalClass);
		}
		return null;
	}

	public static Type discoverFunctionTypeFromFunctionMethod(Method functionMethod) {
		Assert.isTrue(
				functionMethod.getName().equals("apply") ||
				functionMethod.getName().equals("accept") ||
				functionMethod.getName().equals("get"),
				"Only Supplier, Function or Consumer supported at the moment. Was " + functionMethod.getDeclaringClass());

		if (functionMethod.getName().equals("apply")) {
			return ResolvableType.forClassWithGenerics(Function.class,
					ResolvableType.forMethodParameter(functionMethod, 0),
					ResolvableType.forMethodReturnType(functionMethod)).getType();

		}
		else if (functionMethod.getName().equals("accept")) {
			return ResolvableType.forClassWithGenerics(Consumer.class,
					ResolvableType.forMethodParameter(functionMethod, 0)).getType();
		}
		else {
			return ResolvableType.forClassWithGenerics(Supplier.class,
					ResolvableType.forMethodReturnType(functionMethod)).getType();
		}
	}

	public static int getInputCount(Type functionType) {
		assertSupportedTypes(functionType);
		int inputCount = isSupplier(functionType) ? 0 : 1;
		if (functionType instanceof ParameterizedType && !isSupplier(functionType)) {
			Type inputType = ((ParameterizedType) functionType).getActualTypeArguments()[0];
			if (isMulti(inputType)) {
				inputCount = ((ParameterizedType) inputType).getActualTypeArguments().length;
			}
		}
		return inputCount;
	}

	public static int getOutputCount(Type functionType) {
		assertSupportedTypes(functionType);
		int outputCount = isConsumer(functionType) ? 0 : 1;
		if (functionType instanceof ParameterizedType && !isConsumer(functionType)) {
			Type outputType = ((ParameterizedType) functionType).getActualTypeArguments()[isSupplier(functionType) ? 0 : 1];
			if (isMulti(outputType)) {
				outputCount = ((ParameterizedType) outputType).getActualTypeArguments().length;
			}
		}
		return outputCount;
	}

	/**
	 * Returns input type of function type that represents Function or Consumer.
	 * @param functionType  the Type of Function or Consumer
	 * @return the input type as {@link Type}
	 */
	@SuppressWarnings("unchecked")
	public static Type getInputType(Type functionType) {
		if (isSupplier(functionType)) {
			logger.debug("Supplier does not have input type, returning null as input type.");
			return null;
		}
		assertSupportedTypes(functionType);

		Type inputType;
		if (functionType instanceof Class) {
			functionType = Function.class.isAssignableFrom((Class<?>) functionType)
					? TypeResolver.reify(Function.class, (Class<Function<?, ?>>) functionType)
					: TypeResolver.reify(Consumer.class, (Class<Consumer<?>>) functionType);
		}

		inputType = functionType instanceof ParameterizedType
				? ((ParameterizedType) functionType).getActualTypeArguments()[0]
				: Object.class;

		return inputType;
	}

	@SuppressWarnings("rawtypes")
	public static Type discoverFunctionType(Object function, String functionName, GenericApplicationContext applicationContext) {
		if (function instanceof RoutingFunction) {
			return FunctionType.of(FunctionContextUtils.findType(applicationContext.getBeanFactory(), functionName)).getType();
		}
		else if (function instanceof FunctionRegistration) {
			return ((FunctionRegistration) function).getType().getType();
		}
		if (applicationContext.containsBean(functionName + FunctionRegistration.REGISTRATION_NAME_SUFFIX)) { // for Kotlin primarily
			FunctionRegistration fr = applicationContext
					.getBean(functionName + FunctionRegistration.REGISTRATION_NAME_SUFFIX, FunctionRegistration.class);
			return fr.getType().getType();
		}

		boolean beanDefinitionExists = false;
		String functionBeanDefinitionName = discoverDefinitionName(functionName, applicationContext);
		beanDefinitionExists = applicationContext.getBeanFactory().containsBeanDefinition(functionBeanDefinitionName);
		if (applicationContext.containsBean("&" + functionName)) {
			Class<?> objectType = applicationContext.getBean("&" + functionName, FactoryBean.class)
				.getObjectType();
			return FunctionTypeUtils.discoverFunctionTypeFromClass(objectType);
		}

		Type type = FunctionTypeUtils.discoverFunctionTypeFromClass(function.getClass());
		if (beanDefinitionExists) {
			Type t = FunctionTypeUtils.getImmediateGenericType(type, 0);
			if (t == null || t == Object.class) {
				type = FunctionType.of(FunctionContextUtils.findType(applicationContext.getBeanFactory(), functionBeanDefinitionName)).getType();
			}
		}
		else if (!(type instanceof ParameterizedType)) {
			String beanDefinitionName = discoverBeanDefinitionNameByQualifier(applicationContext.getBeanFactory(), functionName);
			if (StringUtils.hasText(beanDefinitionName)) {
				type = FunctionType.of(FunctionContextUtils.findType(applicationContext.getBeanFactory(), beanDefinitionName)).getType();
			}
		}
		return type;
	}

	public static String discoverBeanDefinitionNameByQualifier(ListableBeanFactory beanFactory, String qualifier) {
		Map<String, Object> beanMap =  BeanFactoryAnnotationUtils.qualifiedBeansOfType(beanFactory, Object.class, qualifier);
		if (!CollectionUtils.isEmpty(beanMap) && beanMap.size() == 1) {
			return beanMap.keySet().iterator().next();
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static Type getOutputType(Type functionType) {
		assertSupportedTypes(functionType);
		if (isConsumer(functionType)) {
			logger.debug("Consumer does not have output type, returning null as output type.");
			return null;
		}
		Type outputType;
		if (functionType instanceof Class) {
			functionType = Function.class.isAssignableFrom((Class<?>) functionType)
					? TypeResolver.reify(Function.class, (Class<Function<?, ?>>) functionType)
					: TypeResolver.reify(Supplier.class, (Class<Supplier<?>>) functionType);
		}

		outputType = functionType instanceof ParameterizedType
				? (isSupplier(functionType) ? ((ParameterizedType) functionType).getActualTypeArguments()[0] : ((ParameterizedType) functionType).getActualTypeArguments()[1])
				: Object.class;

		return outputType;
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
		return TypeResolver.resolveRawClass(type, null) == Flux.class;
	}

	public static boolean isCollectionOfMessage(Type type) {
		if (isMessage(type) && isTypeCollection(type)) {
			return isMessage(getImmediateGenericType(type, 0));
		}
		return false;
	}

	public static boolean isMessage(Type type) {
		if (isPublisher(type)) {
			type = getImmediateGenericType(type, 0);
		}

		if (type instanceof ParameterizedType && !Message.class.isAssignableFrom(TypeResolver.resolveRawClass(type, null))) {
			type = getImmediateGenericType(type, 0);
		}
		return Message.class.isAssignableFrom(TypeResolver.resolveRawClass(type, null));
	}

	/**
	 * Determines if input argument to a Function is an array.
	 * @param functionType the function type
	 * @return true if input type is an array, otherwise false
	 */
	public static boolean isOutputArray(Type functionType) {
		Type outputType = FunctionTypeUtils.getOutputType(functionType);
		return outputType instanceof GenericArrayType || outputType instanceof Class && ((Class<?>) outputType).isArray();
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
			if (TypeResolver.resolveRawClass(type, null).isArray()) {
				return false;
			}
			Class<?> clazz = TypeResolver.resolveRawClass(TypeResolver.reify(type), null);
			return clazz.getName().startsWith("reactor.util.function.Tuple");
		}
		return false;
	}

	static Type fromFunctionMethod(Method functionalMethod) {
		Type[] parameterTypes = functionalMethod.getGenericParameterTypes();

		Type functionType = null;
		switch (parameterTypes.length) {
		case 0:
			functionType =  ResolvableType.forClassWithGenerics(Supplier.class,
					ResolvableType.forMethodReturnType(functionalMethod)).getType();
			break;
		case 1:
			if (Void.class.isAssignableFrom(functionalMethod.getReturnType())) {
				functionType =  ResolvableType.forClassWithGenerics(Consumer.class,
						ResolvableType.forMethodParameter(functionalMethod, 0)).getType();
			}
			else {
				functionType =  ResolvableType.forClassWithGenerics(Function.class,
						ResolvableType.forMethodParameter(functionalMethod, 0),
						ResolvableType.forMethodReturnType(functionalMethod)).getType();
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
			Assert.isTrue(type instanceof Class<?>, "Must be one of Supplier, Function, Consumer"
					+ " or FunctionRegistration. Was " + type);
		}

		Class<?> candidateType = (Class<?>) type;

		Assert.isTrue(Supplier.class.isAssignableFrom(candidateType)
				|| Function.class.isAssignableFrom(candidateType)
				|| Consumer.class.isAssignableFrom(candidateType)
				|| FunctionRegistration.class.isAssignableFrom(candidateType)
				|| type.getTypeName().startsWith("org.springframework.context.annotation.ConfigurationClassEnhancer"), "Must be one of Supplier, Function, Consumer"
						+ " or FunctionRegistration. Was " + type);
	}

	private static Type extractReactiveType(Type type) {
		if (type instanceof ParameterizedType && FunctionRegistration.class.isAssignableFrom(((Class<?>) ((ParameterizedType) type).getRawType()))) {
			type = getImmediateGenericType(type, 0);
			if (type instanceof ParameterizedType) {
				type = getImmediateGenericType(type, 0);
			}
		}
		return type;
	}

	private static String discoverDefinitionName(String functionDefinition, GenericApplicationContext applicationContext) {
		String[] aliases = applicationContext.getAliases(functionDefinition);
		for (String alias : aliases) {
			if (applicationContext.getBeanFactory().containsBeanDefinition(alias)) {
				return alias;
			}
		}
		return functionDefinition;
	}

	private static boolean isFunctional(Type type) {
		if (type instanceof ParameterizedType) {
			type = ((ParameterizedType) type).getRawType();
			Assert.isTrue(type instanceof Class<?>, "Must be one of Supplier, Function, Consumer"
					+ " or FunctionRegistration. Was " + type);
		}

		Class<?> candidateType = (Class<?>) type;
		return Supplier.class.isAssignableFrom(candidateType)
				|| Function.class.isAssignableFrom(candidateType)
				|| Consumer.class.isAssignableFrom(candidateType)
				|| BiFunction.class.isAssignableFrom(candidateType)
				|| BiConsumer.class.isAssignableFrom(candidateType);
	}
}
