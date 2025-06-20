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
import kotlinx.coroutines.suspendCancellableCoroutine
import reactor.core.publisher.Flux
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

/**
 * @author Adrien Poupard
 *
 */
private inline fun <O> executeInCoroutineAndConvertToFlux(crossinline block: (Continuation<O>) -> O): Flux<O> {
	return mono(Dispatchers.Unconfined) {
		suspendCancellableCoroutine { continuation ->
			try {
				val result = block(continuation)
				if (result != kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) {
					continuation.resume(result)
				}
			} catch (e: Exception) {
				continuation.resumeWithException(e)
			}
		}
	}.flatMapMany {
		it.convertToFlux()
	}
}

/**
 * Convert a value to a Flux, handling different types appropriately
 *
 * @param value The value to convert
 * @return The value as a Flux
 */
private fun <T> T?.convertToFlux(): Flux<T & Any> {
	return when (this) {
		is Flow<*> -> @Suppress("UNCHECKED_CAST") ((this as Flow<T & Any>).asFlux())
		is Flux<*> -> @Suppress("UNCHECKED_CAST") (this as Flux<T & Any>)
		is Mono<*> -> @Suppress("UNCHECKED_CAST") (this.flatMapMany { Flux.just(it) } as Flux<T & Any>)
		null -> Flux.empty()
		else -> Flux.just(this)
	}
}

fun <I, O> invokeSuspendingFlowFunction(kotlinLambdaTarget: Any, arg0: Flow<I>): Flux<O> {
	try {
		@Suppress("UNCHECKED_CAST")
		val function = kotlinLambdaTarget as SuspendFunction<Flow<I>, O>
		return executeInCoroutineAndConvertToFlux { continuation ->
			function.invoke(arg0, continuation)
		}
	} catch (e: ClassCastException) {
		throw IllegalArgumentException("Unsupported target type: " + kotlinLambdaTarget.javaClass, e)
	}
}

fun <I, O> invokeSuspendingSingleFunction(kotlinLambdaTarget: Any, arg0: I): Flux<O> {
	try {
		@Suppress("UNCHECKED_CAST")
		val function = kotlinLambdaTarget as SuspendFunction<I, O>
		return executeInCoroutineAndConvertToFlux { continuation ->
			function.invoke(arg0, continuation)
		}
	} catch (e: ClassCastException) {
		throw IllegalArgumentException("Unsupported target type: " + kotlinLambdaTarget.javaClass, e)
	}
}

fun <O> invokeSuspendingSupplier(kotlinLambdaTarget: Any): Flux<O> {
	try {
		@Suppress("UNCHECKED_CAST")
		val supplier = kotlinLambdaTarget as SuspendSupplier<O>
		return executeInCoroutineAndConvertToFlux { continuation ->
			supplier.invoke(continuation)
		}
	} catch (e: ClassCastException) {
		throw IllegalArgumentException("Unsupported target type: " + kotlinLambdaTarget.javaClass, e)
	}
}

fun <I> invokeSuspendingConsumer(kotlinLambdaTarget: Any, arg0: I) {
	try {
		@Suppress("UNCHECKED_CAST")
		val consumer = kotlinLambdaTarget as SuspendConsumer<I>
		executeInCoroutineAndConvertToFlux { continuation ->
			consumer.invoke(arg0, continuation)
		}.subscribe()
	} catch (e: ClassCastException) {
		throw IllegalArgumentException("Unsupported target type: " + kotlinLambdaTarget.javaClass, e)
	}
}

fun <I: Any> invokeSuspendingConsumerFlow(kotlinLambdaTarget: Any, arg0: Flux<I>) {
	try {
		@Suppress("UNCHECKED_CAST")
		val consumer = kotlinLambdaTarget as SuspendConsumer<Flow<I>>
		executeInCoroutineAndConvertToFlux { continuation ->
			val flow = arg0.asFlow()
			consumer.invoke(flow, continuation)
		}.subscribe()
	} catch (e: ClassCastException) {
		throw IllegalArgumentException("Unsupported target type: " + kotlinLambdaTarget.javaClass, e)
	}
}

private typealias SuspendFunction<I, O> = Function2<I, Continuation<O>, O>

private typealias SuspendConsumer<I> = Function2<I, Continuation<Unit>, Unit>

private typealias SuspendSupplier<O> = Function1<Continuation<O>, O>
