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
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import reactor.core.publisher.Mono

/**
 * @author Adrien Poupard
 *
 */
private inline fun <T, R> executeInCoroutineAndConvertToFlux(crossinline block: (Continuation<T>) -> R): Flux<Any> {
	return mono(Dispatchers.Unconfined) {
		suspendCoroutineUninterceptedOrReturn { continuation ->
			block(continuation)
		}
	}.flatMapMany {
		asFlux(it)
	}
}

/**
 * Convert a value to a Flux, handling different types appropriately
 *
 * @param value The value to convert
 * @return The value as a Flux
 */
private fun asFlux(value: Any?): Flux<Any> {
	return when (value) {
		is Flow<*> -> (value as Flow<Any>).asFlux()
		is Flux<*> -> value as Flux<Any>
		is Mono<*> -> value.flatMapMany { Flux.just(it) }
		else -> Flux.just(value)
	}
}

fun invokeSuspendingFluxFunction(kotlinLambdaTarget: Any, arg0: Any, shouldConvertFlowAsFlux: Boolean): Flux<Any> {
	val function = kotlinLambdaTarget as SuspendFunction
	val flux = arg0 as Flux<Any>
	return executeInCoroutineAndConvertToFlux { continuation ->
		if(shouldConvertFlowAsFlux) {
			function.invoke(flux.asFlow(), continuation)
		} else {
			function.invoke(flux, continuation)
		}
	}
}

fun invokeSuspendingSingleFunction(kotlinLambdaTarget: Any, arg0: Any): Flux<Any> {
	val function = kotlinLambdaTarget as SuspendFunction
	return executeInCoroutineAndConvertToFlux { continuation ->
		function.invoke(arg0, continuation)
	}
}


fun invokeFluxFunction(kotlinLambdaTarget: Any, arg0: Any, shouldConvertFlowAsFlux: Boolean): Flux<Any> {
	val function = kotlinLambdaTarget as Function // (Any?) -> Any?
	val flux = arg0 as Flux<Any>
	val actualArg: Any? = if (shouldConvertFlowAsFlux) flux.asFlow() else flux
	return try {
		asFlux(function.invoke(actualArg))
	} catch (e: Exception) {
		Flux.error(e)
	}
}

fun invokeSuspendingSupplier(kotlinLambdaTarget: Any): Flux<Any> {
	val supplier = kotlinLambdaTarget as SuspendSupplier
	return executeInCoroutineAndConvertToFlux {  continuation ->
		supplier.invoke(continuation)
	}
}

fun invokeSuspendingConsumer(kotlinLambdaTarget: Any, arg0: Any) {
	val consumer = kotlinLambdaTarget as SuspendConsumer
	val flux = arg0 as Flux<Any>
	executeInCoroutineAndConvertToFlux { continuation ->
		consumer.invoke(flux.asFlow(), continuation)
	}.subscribe()
}

fun invokeFluxConsumer(kotlinLambdaTarget: Any, arg0: Any) {
	val consumer = kotlinLambdaTarget as Consumer
	val flux = arg0 as Flux<Any>
	mono(Dispatchers.Unconfined) {
		consumer.invoke(flux.asFlow())
	}.subscribe()
}


fun isValidFluxFunction(kotlinLambdaTarget: Any, arg0: Any): Boolean {
	return arg0 is Flux<*>
}

fun isValidFluxConsumer(kotlinLambdaTarget: Any, arg0: Any): Boolean {
	return arg0 is Flux<*>
}


private typealias Function = (Any?) -> Any?
private typealias SuspendFunction = (Any?, Continuation<Any>) -> Any?

private typealias Consumer = (Any?) -> Unit?
private typealias SuspendConsumer = (Any?, Continuation<Unit>) -> Unit?

private typealias Supplier = () -> Any?
private typealias SuspendSupplier = (Continuation<Any>) -> Any?
