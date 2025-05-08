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
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Examples of implementing consumers using Kotlin's function type.
 * 
 * ## List of Combinations Implemented:
 * --- Coroutine ---
 * 1. (T) -> Unit                 -> consumerKotlinPlain
 * 2. (Flow<T>) -> Unit           -> consumerKotlinFlow
 * 3. suspend (T) -> Unit         -> consumerKotlinSuspendPlain
 * 4. suspend (Flow<T>) -> Unit   -> consumerKotlinSuspendFlow
 * --- Reactor ---
 * 5. (T) -> Mono<Void>           -> consumerKotlinMonoInput
 * 6. (Mono<T>) -> Mono<Void>     -> consumerKotlinMono
 * 7. (Flux<T>) -> Mono<Void>     -> consumerKotlinFlux
 * --- Message<T> ---
 * 8. (Message<T>) -> Unit        -> consumerKotlinMessage
 * 9. (Mono<Message<T>>) -> Mono<Void> -> consumerKotlinMonoMessage
 * 10. suspend (Message<T>) -> Unit -> consumerKotlinSuspendMessage
 * 11. (Flux<Message<T>>) -> Unit  -> consumerKotlinFluxMessage
 * 12. (Flow<Message<T>>) -> Unit  -> consumerKotlinFlowMessage
 * 13. suspend (Flow<Message<T>>) -> Unit -> consumerKotlinSuspendFlowMessage
 *
 * @author Adrien Poupard
 */
class KotlinConsumeKotlinExamples


/** 1) (T) -> Unit */
@Component
class ConsumerKotlinPlain : (String) -> Unit {
	override fun invoke(input: String) {
		println("Consumed: $input")
	}
}

/** 2) (Flow<T>) -> Unit */
@Component
class ConsumerKotlinFlow : (Flow<String>) -> Unit {
	override fun invoke(flowInput: Flow<String>) {
		println("Received flow: $flowInput (would collect in coroutine)")
	}
}

/** 3) suspend (T) -> Unit */
@Component
class ConsumerKotlinSuspendPlain : suspend (String) -> Unit {
	override suspend fun invoke(input: String) {
		println("Suspend consumed: $input")
	}
}

/** 4) suspend (Flow<T>) -> Unit */
@Component
class ConsumerKotlinSuspendFlow : suspend (Flow<String>) -> Unit {
	override suspend fun invoke(flowInput: Flow<String>) {
		flowInput.collect { item ->
			println("Flow item consumed: $item")
		}
	}
}

/** 5) (T) -> Mono<Void> */
@Component
class ConsumerKotlinMonoInput : (String) -> Mono<Void> {
	override fun invoke(input: String): Mono<Void> {
		return Mono.fromRunnable<Void> {
			println("[Reactor] Consumed T: $input")
		}
	}
}

/** 6) (Mono<T>) -> Mono<Void> */
@Component
class ConsumerKotlinMono : (Mono<String>) -> Mono<Void> {
	override fun invoke(monoInput: Mono<String>): Mono<Void> {
		return monoInput.doOnNext { item ->
			println("[Reactor] Consumed Mono item: $item")
		}.then()
	}
}

/** 7) (Flux<T>) -> Mono<Void> */
@Component
class ConsumerKotlinFlux : (Flux<String>) -> Mono<Void> {
	override fun invoke(fluxInput: Flux<String>): Mono<Void> {
		return fluxInput.doOnNext { item ->
			println("[Reactor] Consumed Flux item: $item")
		}.then()
	}
}

/** 8) (Message<T>) -> Unit */
@Component
class ConsumerKotlinMessage : (Message<String>) -> Unit {
	override fun invoke(message: Message<String>) {
		println("[Message] Consumed payload: ${message.payload}, Headers: ${message.headers}")
	}
}

/** 9) (Mono<Message<T>>) -> Mono<Void> */
@Component
class ConsumerKotlinMonoMessage : (Mono<Message<String>>) -> Mono<Void> {
	override fun invoke(monoMsgInput: Mono<Message<String>>): Mono<Void> {
		return monoMsgInput
			.doOnNext { message ->
				println("[Message][Mono] Consumed payload: ${message.payload}, Header id: ${message.headers.id}")
			}
			.then()
	}
}

/** 10) suspend (Message<T>) -> Unit */
@Component
class ConsumerKotlinSuspendMessage : suspend (Message<String>) -> Unit {
	override suspend fun invoke(message: Message<String>) {
		println("[Message][Suspend] Consumed payload: ${message.payload}, Header count: ${message.headers.size}")
	}
}

/** 11) (Flux<Message<T>>) -> Unit */
@Component
class ConsumerKotlinFluxMessage : (Flux<Message<String>>) -> Unit {
	override fun invoke(fluxMsgInput: Flux<Message<String>>) {
		// Explicit subscription needed here because the lambda itself returns Unit
		fluxMsgInput.subscribe { message ->
			println("[Message] Consumed Flux payload: ${message.payload}, Headers: ${message.headers}")
		}
	}
}

/** 12) (Flow<Message<T>>) -> Unit */
@Component
class ConsumerKotlinFlowMessage : (Flow<Message<String>>) -> Unit {
	override fun invoke(flowMsgInput: Flow<Message<String>>) {
		// Similar to Flux consumer returning Unit, explicit collection might be needed depending on context.
		println("[Message] Received Flow: $flowMsgInput (would need explicit collection)")
	}
}

/** 13) suspend (Flow<Message<T>>) -> Unit */
@Component
class ConsumerKotlinSuspendFlowMessage : suspend (Flow<Message<String>>) -> Unit {
	override suspend fun invoke(flowMsgInput: Flow<Message<String>>) {
		flowMsgInput.collect { message ->
			println("[Message] Consumed Suspend Flow payload: ${message.payload}, Headers: ${message.headers}")
		}
	}
}

