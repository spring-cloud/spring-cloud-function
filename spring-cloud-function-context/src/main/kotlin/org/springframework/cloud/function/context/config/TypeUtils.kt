/*
 * Copyright 2021-2021 the original author or authors.
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

@file:JvmName("TypeUtils")
package org.springframework.cloud.function.context.config

import kotlinx.coroutines.flow.Flow
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.coroutines.Continuation
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.asFlux
import org.springframework.core.ResolvableType
import reactor.core.publisher.Flux

/**
 * @author Adrien Poupard
 *
 */
fun getSuspendingFunctionArgType(type: Type): ResolvableType {
	return ResolvableType.forType(getFlowTypeArguments(type))
}

fun getSuspendingFunctionReturnType(type: Type): ResolvableType {
	val lower = getContinuationTypeArguments(type)
	return ResolvableType.forType(getFlowTypeArguments(lower))
}

fun getFlowTypeArguments(type: Type): Type {
	if(!isFlowType(type)) {
		return type
	}
	val parameterizedLowerType = type as ParameterizedType
	if(parameterizedLowerType.actualTypeArguments.isEmpty()) {
		return parameterizedLowerType
	}

	val actualTypeArgument = parameterizedLowerType.actualTypeArguments[0]
	return if(actualTypeArgument is WildcardType) {
		val wildcardTypeLower = parameterizedLowerType.actualTypeArguments[0] as WildcardType
		wildcardTypeLower.upperBounds[0]
	} else {
		actualTypeArgument
	}
}

fun hasFlowType(types: Array<Type>) : Boolean {
	return types.any { isFlowType(it) }
}

fun isFlowType(type: Type): Boolean {
	return type.typeName.startsWith(Flow::class.qualifiedName!!)
}

fun isContinuationType(type: Type): Boolean {
	return type.typeName.startsWith(Continuation::class.qualifiedName!!)
}

fun isUnitType(type: Type): Boolean {
	return isTypeRepresentedByClass(type, Unit::class.java)
}

fun isVoidType(type: Type): Boolean {
	return isTypeRepresentedByClass(type, Void::class.java)
}

fun isContinuationUnitType(type: Type): Boolean {
	return isContinuationType(type) && type.typeName.contains(Unit::class.qualifiedName!!)
}

fun isContinuationFlowType(type: Type): Boolean {
	return isContinuationType(type) && type.typeName.contains(Flow::class.qualifiedName!!)
}

internal fun getContinuationTypeArguments(type: Type): Type {
	if(!isContinuationType(type)) {
		return type
	}
	val parameterizedType = type as ParameterizedType
	return when (val typeArg = parameterizedType.actualTypeArguments[0]) {
		is WildcardType -> typeArg.lowerBounds[0]
		is ParameterizedType -> typeArg
		else -> typeArg
	}
}

fun <T : Any> convertToFlow(arg0: Flux<T>): Flow<T> {
	return arg0.asFlow()
}

fun <T : Any> convertToFlux(arg0: Flow<T>): Flux<T> {
	return arg0.asFlux()
}
