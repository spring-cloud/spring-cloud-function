/*
 * Copyright 2019-2022 the original author or authors.
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

package org.springframework.cloud.function.utils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;

import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.reactive.ReactiveFlowKt;
import kotlinx.coroutines.reactor.ReactorFlowKt;
import reactor.core.publisher.Flux;

import org.springframework.core.KotlinDetector;
import org.springframework.core.ResolvableType;

/**
 * Utility methods for working with Kotlin types.
 *
 * @author Oleg Zhurakousky
 * @author Adrien Poupard
 */
public final class KotlinUtils {
	private KotlinUtils() {
		// Utility class should not be instantiated
	}

	public static boolean isKotlinType(Object object, Type functionType) {
		if (KotlinDetector.isKotlinPresent()) {
			boolean isKotlinObject = KotlinDetector.isKotlinType(object.getClass())
					|| object instanceof Function0<?>
					|| object instanceof Function1<?, ?>;
			if (isKotlinObject) {
				return true;
			}
			// Check if there is a flow type in the functionType it will be converted to a Flux
			else if (functionType instanceof ParameterizedType) {
				Type[] types = ((ParameterizedType) functionType).getActualTypeArguments();
				return hasFlowType(types);
			}
			return false;
		}
		return false;
	}

	/**
	 * Get the argument type of a suspending function.
	 *
	 * @param type the function type
	 * @return the resolved argument type
	 */
	public static ResolvableType getSuspendingFunctionArgType(Type type) {
		return ResolvableType.forType(getFlowTypeArguments(type));
	}

	/**
	 * Get the return type of a suspending function.
	 *
	 * @param type the function type
	 * @return the resolved return type
	 */
	public static ResolvableType getSuspendingFunctionReturnType(Type type) {
		Type lower = getContinuationTypeArguments(type);
		return ResolvableType.forType(getFlowTypeArguments(lower));
	}

	/**
	 * Get the type arguments of a Flow type.
	 *
	 * @param type the Flow type
	 * @return the type arguments
	 */
	public static Type getFlowTypeArguments(Type type) {
		if (!isFlowType(type)) {
			return type;
		}
		ParameterizedType parameterizedLowerType = (ParameterizedType) type;
		if (parameterizedLowerType.getActualTypeArguments().length == 0) {
			return parameterizedLowerType;
		}

		Type actualTypeArgument = parameterizedLowerType.getActualTypeArguments()[0];
		if (actualTypeArgument instanceof WildcardType) {
			WildcardType wildcardTypeLower = (WildcardType) parameterizedLowerType.getActualTypeArguments()[0];
			return wildcardTypeLower.getUpperBounds()[0];
		}
		else {
			return actualTypeArgument;
		}
	}

	/**
	 * Check if any of the types is a Flow type.
	 *
	 * @param types the types to check
	 * @return true if any type is a Flow type
	 */
	public static boolean hasFlowType(Type[] types) {
		for (Type type : types) {
			if (isFlowType(type)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check if the type is a Flow type.
	 *
	 * @param type the type to check
	 * @return true if the type is a Flow type
	 */
	public static boolean isFlowType(Type type) {
		return type.getTypeName().startsWith(Flow.class.getName());
	}

	/**
	 * Check if the type is a Continuation type.
	 *
	 * @param type the type to check
	 * @return true if the type is a Continuation type
	 */
	public static boolean isContinuationType(Type type) {
		return type.getTypeName().startsWith(Continuation.class.getName());
	}

	/**
	 * Check if the type is a Unit type.
	 *
	 * @param type the type to check
	 * @return true if the type is a Unit type
	 */
	public static boolean isUnitType(Type type) {
		return isTypeRepresentedByClass(type, kotlin.Unit.class);
	}

	/**
	 * Check if the type is a Void type.
	 *
	 * @param type the type to check
	 * @return true if the type is a Void type
	 */
	public static boolean isVoidType(Type type) {
		return isTypeRepresentedByClass(type, Void.class);
	}

	/**
	 * Check if the type is a Continuation of Unit type.
	 *
	 * @param type the type to check
	 * @return true if the type is a Continuation of Unit type
	 */
	public static boolean isContinuationUnitType(Type type) {
		return isContinuationType(type) && type.getTypeName().contains(kotlin.Unit.class.getName());
	}

	/**
	 * Check if the type is a Continuation of Flow type.
	 *
	 * @param type the type to check
	 * @return true if the type is a Continuation of Flow type
	 */
	public static boolean isContinuationFlowType(Type type) {
		return isContinuationType(type) && type.getTypeName().contains(Flow.class.getName());
	}

	/**
	 * Get the type arguments of a Continuation type.
	 *
	 * @param type the Continuation type
	 * @return the type arguments
	 */
	public static Type getContinuationTypeArguments(Type type) {
		if (!isContinuationType(type)) {
			return type;
		}
		ParameterizedType parameterizedType = (ParameterizedType) type;
		Type typeArg = parameterizedType.getActualTypeArguments()[0];

		if (typeArg instanceof WildcardType) {
			return ((WildcardType) typeArg).getLowerBounds()[0];
		}
		else if (typeArg instanceof ParameterizedType) {
			return typeArg;
		}
		else {
			return typeArg;
		}
	}

	/**
	 * Convert a Flux to a Flow.
	 *
	 * @param arg0 the Flux to convert
	 * @param <T> the element type
	 * @return the converted Flow
	 */
	public static <T> Flow<T> convertToFlow(Flux<T> arg0) {
		return ReactiveFlowKt.asFlow(arg0);
	}

	/**
	 * Convert a Flow to a Flux.
	 *
	 * @param arg0 the Flow to convert
	 * @param <T> the element type
	 * @return the converted Flux
	 */
	public static <T> Flux<T> convertToFlux(Flow<T> arg0) {
		return ReactorFlowKt.asFlux(arg0);
	}

	/**
	 * Check if a type is represented by a specific class.
	 *
	 * @param type the type to check
	 * @param clazz the class to check against
	 * @return true if the type is represented by the class
	 */
	private static boolean isTypeRepresentedByClass(Type type, Class<?> clazz) {
		return type.getTypeName().contains(clazz.getName());
	}
}
