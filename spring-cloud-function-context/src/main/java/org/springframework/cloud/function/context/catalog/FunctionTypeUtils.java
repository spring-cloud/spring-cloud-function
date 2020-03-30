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

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import net.jodah.typetools.TypeResolver;
import org.reactivestreams.Publisher;
import reactor.util.function.Tuple2;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.catalog.BeanFactoryAwareFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.core.ResolvableType;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Set of utility operations to interrogate function definitions.
 *
 * @author Oleg Zhurakousky
 * @since 3.0
 */
public final class FunctionTypeUtils {

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
		if (isMessage(type)) {
			type = getImmediateGenericType(type, 0);
		}
		Type rawType = type instanceof ParameterizedType ? ((ParameterizedType) type).getRawType() : type;

		return rawType instanceof Class<?> && Collection.class.isAssignableFrom((Class<?>) rawType);
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

	public static Type discoverFunctionTypeFromFunctionalObject(Object functionalObject) {
		if (functionalObject instanceof FunctionInvocationWrapper) {
			return ((FunctionInvocationWrapper) functionalObject).getFunctionType();
		}
		else {
			return discoverFunctionTypeFromClass(functionalObject.getClass());
		}
	}

	@SuppressWarnings("unchecked")
	public static Type discoverFunctionTypeFromClass(Class<?> functionalClass) {
		Assert.isTrue(isFunctional(functionalClass), "Type must be one of Supplier, Function or Consumer");

		if (Function.class.isAssignableFrom(functionalClass)) {
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

	public static Type unwrapActualTypeByIndex(Type type, int index) {
		if (isMessage(type) || isPublisher(type)) {
			if (isPublisher(type)) {
				return unwrapActualTypeByIndex(FunctionTypeUtils.getImmediateGenericType(type, index), index);
			}
			else if (isMessage(type)) {
				return unwrapActualTypeByIndex(FunctionTypeUtils.getImmediateGenericType(type, index), index);
			}
		}
		return type;
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

	public static Type getInputType(Type functionType, int index) {
		assertSupportedTypes(functionType);
		if (isSupplier(functionType)) {
			return getOutputType(functionType, index);
		}
		Type inputType = isSupplier(functionType) ? null :  Object.class;
		if ((isFunction(functionType) || isConsumer(functionType)) && functionType instanceof ParameterizedType) {
			inputType = ((ParameterizedType) functionType).getActualTypeArguments()[0];
			inputType = isMulti(inputType)
					? ((ParameterizedType) inputType).getActualTypeArguments()[index]
							: inputType;
		}

		return inputType;
	}

	public static Type getOutputType(Type functionType, int index) {
		assertSupportedTypes(functionType);
		Type outputType = isConsumer(functionType) ? null :  Object.class;
		if ((isFunction(functionType) || isSupplier(functionType)) && functionType instanceof ParameterizedType) {
			outputType = ((ParameterizedType) functionType).getActualTypeArguments()[isFunction(functionType) ? 1 : 0];
			outputType = isMulti(outputType)
					? ((ParameterizedType) outputType).getActualTypeArguments()[index]
							: outputType;
		}

		return outputType;
	}

	public static Type getImmediateGenericType(Type type, int index) {
		if (type instanceof ParameterizedType) {
			return ((ParameterizedType) type).getActualTypeArguments()[index];
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static Class<? extends Publisher<?>> getPublisherType(Type type) {
		if (type instanceof ParameterizedType && isReactive(type)) {
			return (Class<? extends Publisher<?>>) ((ParameterizedType) type).getRawType();
		}
		throw new IllegalStateException("The provided type is not a Publisher");
	}

	public static boolean isPublisher(Type type) {
		return isFlux(type) || isMono(type);
	}

	public static boolean isFlux(Type type) {
		type = extractReactiveType(type);
		return type.getTypeName().startsWith("reactor.core.publisher.Flux");
	}

	public static boolean isMessage(Type type) {
		if (isPublisher(type)) {
			type = getImmediateGenericType(type, 0);
		}
		return type.getTypeName().startsWith("org.springframework.messaging.Message");
	}

	/**
	 * Determines if input argument to a Function is an array.
	 * @param functionType the function type
	 * @return true if input type is an array, otherwise false
	 */
	public static boolean isInputArray(Type functionType) {
		Type inputType = FunctionTypeUtils.getInputType(functionType, 0);
		return inputType instanceof GenericArrayType || inputType instanceof Class && ((Class<?>) inputType).isArray();
	}

	/**
	 * Determines if input argument to a Function is an array.
	 * @param functionType the function type
	 * @return true if input type is an array, otherwise false
	 */
	public static boolean isOutputArray(Type functionType) {
		Type outputType = FunctionTypeUtils.getOutputType(functionType, 0);
		return outputType instanceof GenericArrayType || outputType instanceof Class && ((Class<?>) outputType).isArray();
	}

	/**
	 * Evaluates if provided type is an assignable to {@link Publisher}.
	 * @param type type to evaluate
	 * @return true is provided type is an assignable to {@link Publisher}
	 */
	public static boolean isReactive(Type type) {
		Class<?> rawType = type instanceof ParameterizedType
				? (Class<?>) ((ParameterizedType) type).getRawType() : (type instanceof Class<?> ? (Class<?>) type : Object.class);
		return Publisher.class.isAssignableFrom(rawType);
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

	public static boolean isOfType(Type type, Class<?> cls) {
		if (type instanceof Class) {
			return cls.isAssignableFrom((Class<?>) type);
		}
		else if (type instanceof ParameterizedType) {
			return isOfType(((ParameterizedType) type).getRawType(), cls);
		}
		return false;
	}

	public static boolean isMono(Type type) {
		type = extractReactiveType(type);
		return type.getTypeName().startsWith("reactor.core.publisher.Mono");
	}

	public static boolean isFunctional(Type type) {
		if (type instanceof ParameterizedType) {
			type = ((ParameterizedType) type).getRawType();
			Assert.isTrue(type instanceof Class<?>, "Must be one of Supplier, Function, Consumer"
					+ " or FunctionRegistration. Was " + type);
		}

		Class<?> candidateType = (Class<?>) type;
		return Supplier.class.isAssignableFrom(candidateType)
				|| Function.class.isAssignableFrom(candidateType)
				|| Consumer.class.isAssignableFrom(candidateType);
	}

	public static boolean isMultipleInputArguments(Type functionType) {
		boolean multipleInputs = false;
		if (functionType instanceof ParameterizedType && !isSupplier(functionType)) {
			Type inputType = ((ParameterizedType) functionType).getActualTypeArguments()[0];
			multipleInputs = isMulti(inputType);
		}
		return multipleInputs;
	}

	public static boolean isMultipleArgumentsHolder(Object argument) {
		return argument != null && argument.getClass().getName().startsWith("reactor.util.function.Tuple");
	}

	public static Type compose(Type originType, Type composedType) {
		ResolvableType resolvableOriginType = ResolvableType.forType(originType);
		ResolvableType resolvableComposedType = ResolvableType.forType(composedType);
		if (FunctionTypeUtils.isSupplier(originType)) {
			if (FunctionTypeUtils.isFunction(composedType)) {
				ResolvableType resolvableLastArgument = resolvableComposedType.getGenerics()[1];
				resolvableLastArgument = FunctionTypeUtils.isPublisher(resolvableOriginType.getGeneric(0).getType())
						? ResolvableType.forClassWithGenerics(resolvableOriginType.getGeneric(0).getRawClass(), resolvableLastArgument)
								: resolvableLastArgument;
						originType = ResolvableType.forClassWithGenerics(Supplier.class, resolvableLastArgument).getType();
			}
		}
		else  {
			ResolvableType outType = FunctionTypeUtils.isConsumer(composedType)
					? ResolvableType.forClass(Void.class)
							: (ObjectUtils.isEmpty(resolvableComposedType.getGenerics())
									? ResolvableType.forClass(Object.class) : resolvableComposedType.getGenerics()[1]);

			originType = ResolvableType.forClassWithGenerics(Function.class,
					ObjectUtils.isEmpty(resolvableOriginType.getGenerics()) ? resolvableOriginType : resolvableOriginType.getGenerics()[0],
							outType).getType();
		}
		return originType;
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
		case 2:
			ResolvableType canonicalParametersWrapper = fromTwoArityFunction(functionalMethod);
			functionType =  ResolvableType.forClassWithGenerics(Function.class,
					canonicalParametersWrapper,
					ResolvableType.forMethodReturnType(functionalMethod)).getType();
			break;
		default:
			throw new UnsupportedOperationException("Functional method: " + functionalMethod + " is not supported");
		}
		return functionType;
	}

	private static ResolvableType fromTwoArityFunction(Method functionalMethod) {
		return  ResolvableType.forClassWithGenerics(Tuple2.class,
				ResolvableType.forMethodParameter(functionalMethod, 0),
				ResolvableType.forMethodParameter(functionalMethod, 1));
	}

	private static boolean isMulti(Type type) {
		return type.getTypeName().startsWith("reactor.util.function.Tuple");
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


}
