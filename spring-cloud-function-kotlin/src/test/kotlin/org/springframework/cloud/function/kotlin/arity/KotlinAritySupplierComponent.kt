/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.cloud.function.kotlin.arity

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.UUID

/**
 * Examples of implementing suppliers using Kotlin's function type.
 * 
 * ## List of Combinations Implemented:
 * --- Coroutine ---
 * 1. () -> R                      -> supplierKotlinPlain
 * 2. () -> Flow<R>               -> supplierKotlinFlow
 * 3. suspend () -> R             -> supplierKotlinSuspendPlain
 * 4. suspend () -> Flow<R>       -> supplierKotlinSuspendFlow
 * --- Reactor ---
 * 5. () -> Mono<R>               -> supplierKotlinMono
 * 6. () -> Flux<R>               -> supplierKotlinFlux
 * --- Message<T> ---
 * 7. () -> Message<R>            -> supplierKotlinMessage
 * 8. () -> Mono<Message<R>>     -> supplierKotlinMonoMessage
 * 9. suspend () -> Message<R>    -> supplierKotlinSuspendMessage
 * 10. () -> Flux<Message<R>>     -> supplierKotlinFluxMessage
 * 11. () -> Flow<Message<R>>     -> supplierKotlinFlowMessage
 * 12. suspend () -> Flow<Message<R>> -> supplierKotlinSuspendFlowMessage
 *
 * @author Adrien Poupard
 */
class KotlinSupplierKotlinExamples

/** 1) () -> R */
@Component
class SupplierKotlinPlain : () -> Int {
	override fun invoke(): Int {
		return 42
	}
}

/** 2) () -> Flow<R> */
@Component
class SupplierKotlinFlow : () -> Flow<String> {
	override fun invoke(): Flow<String> {
		return flow {
			emit("A")
			emit("B")
			emit("C")
		}
	}
}

/** 3) suspend () -> R */
@Component
class SupplierKotlinSuspendPlain : suspend () -> String {
	override suspend fun invoke(): String {
		return "Hello from suspend"
	}
}

/** 4) suspend () -> Flow<R> */
@Component
class SupplierKotlinSuspendFlow : suspend () -> Flow<String> {
	override suspend fun invoke(): Flow<String> {
		return flow {
			emit("x")
			emit("y")
			emit("z")
		}
	}
}

/** 5) () -> Mono<R> */
@Component
class SupplierKotlinMono : () -> Mono<String> {
	override fun invoke(): Mono<String> {
		return Mono.just("Hello from Mono").delayElement(Duration.ofMillis(50))
	}
}

/** 6) () -> Flux<R> */
@Component
class SupplierKotlinFlux : () -> Flux<String> {
	override fun invoke(): Flux<String> {
		return Flux.just("Alpha", "Beta", "Gamma").delayElements(Duration.ofMillis(20))
	}
}

/** 7) () -> Message<R> */
@Component
class SupplierKotlinMessage : () -> Message<String> {
	override fun invoke(): Message<String> {
		return MessageBuilder.withPayload("Hello from Message")
			.setHeader("messageId", UUID.randomUUID().toString())
			.build()
	}
}

/** 8) () -> Mono<Message<R>> */
@Component
class SupplierKotlinMonoMessage : () -> Mono<Message<String>> {
	override fun invoke(): Mono<Message<String>> {
		return Mono.just(
			MessageBuilder.withPayload("Hello from Mono Message")
				.setHeader("monoMessageId", UUID.randomUUID().toString())
				.setHeader("source", "mono")
				.build()
		).delayElement(Duration.ofMillis(40))
	}
}

/** 9) suspend () -> Message<R> */
@Component
class SupplierKotlinSuspendMessage : suspend () -> Message<String> {
	override suspend fun invoke(): Message<String> {
		return MessageBuilder.withPayload("Hello from Suspend Message")
			.setHeader("suspendMessageId", UUID.randomUUID().toString())
			.setHeader("wasSuspended", true)
			.build()
	}
}

/** 10) () -> Flux<Message<R>> */
@Component
class SupplierKotlinFluxMessage : () -> Flux<Message<String>> {
	override fun invoke(): Flux<Message<String>> {
		return Flux.just("Msg1", "Msg2")
			.delayElements(Duration.ofMillis(30))
			.map { payload ->
				MessageBuilder.withPayload(payload)
					.setHeader("fluxMessageId", UUID.randomUUID().toString())
					.build()
			}
	}
}

/** 11) () -> Flow<Message<R>> */
@Component
class SupplierKotlinFlowMessage : () -> Flow<Message<String>> {
	override fun invoke(): Flow<Message<String>> {
		return flow {
			listOf("FlowMsg1", "FlowMsg2").forEach { payload ->
				emit(
					MessageBuilder.withPayload(payload)
						.setHeader("flowMessageId", UUID.randomUUID().toString())
						.build()
				)
			}
		}
	}
}

/** 12) suspend () -> Flow<Message<R>> */
@Component
class SupplierKotlinSuspendFlowMessage : suspend () -> Flow<Message<String>> {
	override suspend fun invoke(): Flow<Message<String>> {
		return flow {
			listOf("SuspendFlowMsg1", "SuspendFlowMsg2").forEach { payload ->
				emit(
					MessageBuilder.withPayload(payload)
						.setHeader("suspendFlowMessageId", UUID.randomUUID().toString())
						.build()
				)
			}
		}
	}
}
