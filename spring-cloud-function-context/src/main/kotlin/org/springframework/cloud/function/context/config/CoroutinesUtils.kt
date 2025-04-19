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

@file:JvmName("CoroutinesUtils")
package org.springframework.cloud.function.context.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.mono
import reactor.core.publisher.Flux
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import reactor.core.publisher.Mono

/**
 * @author Adrien Poupard
 *
 */

fun getSuspendingFunctionArgType(type: Type): Type {
	return getFlowTypeArguments(type)
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

fun isFlowType(type: Type): Boolean {
	return type.typeName.startsWith(Flow::class.qualifiedName!!)
}

fun getSuspendingFunctionReturnType(type: Type): Type {
	val lower = getContinuationTypeArguments(type)
	return getFlowTypeArguments(lower)
}

fun isContinuationType(type: Type): Boolean {
	return type.typeName.startsWith(Continuation::class.qualifiedName!!)
}

fun isUnitType(type: Type): Boolean {
	return isTypeRepresentedByClass(type, Unit::class.java)
}

fun isContinuationUnitType(type: Type): Boolean {
	return isContinuationType(type) && type.typeName.contains(Unit::class.qualifiedName!!)
}

//fun isContinuationFlowType(type: Type): Boolean {
//	return isContinuationType(type) && type.typeName.contains(Flow::class.qualifiedName!!)
//}

private fun getContinuationTypeArguments(type: Type): Type {
	if(!isContinuationType(type)) {
		return type
	}
	val parameterizedType = type as ParameterizedType
	val wildcardType = parameterizedType.actualTypeArguments[0] as WildcardType
	return wildcardType.lowerBounds[0]
}

fun invokeSuspendingFluxFunction(kotlinLambdaTarget: Any, arg0: Any): Flux<Any> {
	val function = kotlinLambdaTarget as SuspendFunction
	val flux = arg0 as Flux<Any>
	return mono(Dispatchers.Unconfined) {
		suspendCoroutineUninterceptedOrReturn<Any> {
			function.invoke(flux.asFlow(), it)
		}
	}.flatMapMany {
		if(it is Flow<*>) {
			(it as Flow<Any>).asFlux()
		} else  {
			Flux.just(it)
		}
	}
}
fun invokeSuspendingSingleFunction(kotlinLambdaTarget: Any, arg0: Any): Flux<Any> {
	val function = kotlinLambdaTarget as SuspendFunction
	return mono(Dispatchers.Unconfined) {
		suspendCoroutineUninterceptedOrReturn<Any> {
			function.invoke(arg0, it)
		}
	}.flatMapMany {
		if(it is Flow<*>) {
			(it as Flow<Any>).asFlux()
		} else  {
			Flux.just(it)
		}
	}
}
fun invokeFluxFunction(kotlinLambdaTarget: Any, arg0: Any): Flux<Any> {
	val function = kotlinLambdaTarget as Function
	val flux = arg0 as Flux<Any>
	return mono(Dispatchers.Unconfined) {
		function.invoke(flux.asFlow())
	}.flatMapMany {
		if(it is Flow<*>) {
			(it as Flow<Any>).asFlux()
		} else  {
			Flux.just(it)
		}
	}
}

fun invokeSuspendingSupplier(kotlinLambdaTarget: Any): Flux<Any> {
	val supplier = kotlinLambdaTarget as SuspendSupplier
	return mono(Dispatchers.Unconfined) {
		suspendCoroutineUninterceptedOrReturn {
			supplier.invoke(it)
		}
	}.flatMapMany {
		if(it is Flow<*>) {
			(it as Flow<Any>).asFlux()
		} else  {
			Flux.just(it)
		}

	}
}

fun invokeSuspendingConsumer(kotlinLambdaTarget: Any, arg0: Any) {
	val consumer = kotlinLambdaTarget as SuspendConsumer
	val flux = arg0 as Flux<Any>
	mono(Dispatchers.Unconfined) {
		suspendCoroutineUninterceptedOrReturn<Unit> {
			consumer.invoke(flux.asFlow(), it)
		}
	}.subscribe()
}

fun invokeFluxConsumer(kotlinLambdaTarget: Any, arg0: Any) {
	val consumer = kotlinLambdaTarget as Consumer
	val flux = arg0 as Flux<Any>
	mono(Dispatchers.Unconfined) {
		consumer.invoke(flux.asFlow())
	}.subscribe()
}

fun isValidSuspendingFunction(kotlinLambdaTarget: Any, arg0: Any): Boolean {
//	return isValidFluxFunction(kotlinLambdaTarget, arg0) && kotlinLambdaTarget is Function2<*, *, *>
	return kotlinLambdaTarget is Function2<*, *, *>
}

fun isValidFluxFunction(kotlinLambdaTarget: Any, arg0: Any): Boolean {
	return arg0 is Flux<*>
}

fun isValidFluxConsumer(kotlinLambdaTarget: Any, arg0: Any): Boolean {
	return arg0 is Flux<*>
}

fun isValidSuspendingSupplier(kotlinLambdaTarget: Any): Boolean {
	return kotlinLambdaTarget is Function1<*, *>
}

private typealias Function = (Any?) -> Any?
private typealias SuspendFunction = (Any?, Continuation<Any>) -> Any?

private typealias Consumer = (Any?) -> Unit?
private typealias SuspendConsumer = (Any?, Continuation<Unit>) -> Unit?

private typealias Supplier = () -> Any?
private typealias SuspendSupplier = (Continuation<Any>) -> Any?
