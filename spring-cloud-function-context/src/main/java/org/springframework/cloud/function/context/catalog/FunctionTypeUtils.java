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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * Set of utility operations to interrogate function definitions.
 *
 * @author Oleg Zhurakousky
 * @since 3.0
 */
final class FunctionTypeUtils {

	private FunctionTypeUtils() {

	}

	/**
	 * Will extract the relevant type (e.g., for type conversion purposes). Useful to extract
	 * types wrapped in Publisher and/or Message.
	 */
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
		int inputCount = isConsumer(functionType) ? 0 : 1;
		if (functionType instanceof ParameterizedType && !isConsumer(functionType)) {
			Type inputType = ((ParameterizedType) functionType).getActualTypeArguments()[isSupplier(functionType) ? 0 : 1];
			if (isMulti(inputType)) {
				inputCount = ((ParameterizedType) inputType).getActualTypeArguments().length;
			}
		}
		return inputCount;
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
							: ((ParameterizedType) functionType).getActualTypeArguments()[index];
		}

		return inputType;
	}

	public static Type getOutputType(Type functionType, int index) {
		assertSupportedTypes(functionType);
		Type inputType = isConsumer(functionType) ? null :  Object.class;
		if ((isFunction(functionType) || isSupplier(functionType)) && functionType instanceof ParameterizedType) {
			inputType = ((ParameterizedType) functionType).getActualTypeArguments()[isFunction(functionType) ? 1 : 0];
			inputType = isMulti(inputType)
					? ((ParameterizedType) inputType).getActualTypeArguments()[index]
							: ((ParameterizedType) functionType).getActualTypeArguments()[index];
		}

		return inputType;
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



	public static boolean isReactive(Type type) {
		if (type instanceof ParameterizedType) {
			return Publisher.class.isAssignableFrom(((Class<?>) ((ParameterizedType) type).getRawType()));
		}
		return false;
	}

	public static boolean isConsumer(Type type) {
		return type.getTypeName().startsWith("java.util.function.Consumer");
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

	private static boolean isMulti(Type type) {
		return type.getTypeName().startsWith("reactor.util.function.Tuple");
	}

	public static boolean isSupplier(Type type) {
		return type.getTypeName().startsWith("java.util.function.Supplier");
	}

	public static boolean isFunction(Type type) {
		return type.getTypeName().startsWith("java.util.function.Function");
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

			originType = ResolvableType.forClassWithGenerics(Function.class, resolvableOriginType.getGenerics()[0], outType).getType();
		}
		return originType;
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
