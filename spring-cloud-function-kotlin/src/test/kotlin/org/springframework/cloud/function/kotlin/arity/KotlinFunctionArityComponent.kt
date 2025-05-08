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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * Examples of implementing functions using Kotlin's function type.
 * 
 * ## List of Combinations Implemented:
 * --- Coroutine ---
 * 1. (T) -> R                     -> functionKotlinPlainToPlain
 * 2. (T) -> Flow<R>               -> functionKotlinPlainToFlow
 * 3. (Flow<T>) -> R               -> functionKotlinFlowToPlain
 * 4. (Flow<T>) -> Flow<R>         -> functionKotlinFlowToFlow
 * 5. suspend (T) -> R             -> functionKotlinSuspendPlainToPlain
 * 6. suspend (T) -> Flow<R>       -> functionKotlinSuspendPlainToFlow
 * 7. suspend (Flow<T>) -> R       -> functionKotlinSuspendFlowToPlain
 * 8. suspend (Flow<T>) -> Flow<R> -> functionKotlinSuspendFlowToFlow
 * --- Reactor ---
 * 9. (T) -> Mono<R>               -> functionKotlinPlainToMono
 * 10. (T) -> Flux<R>              -> functionKotlinPlainToFlux
 * 11. (Mono<T>) -> Mono<R>        -> functionKotlinMonoToMono
 * 12. (Flux<T>) -> Flux<R>        -> functionKotlinFluxToFlux
 * 13. (Flux<T>) -> Mono<R>        -> functionKotlinFluxToMono
 * --- Message<T> ---
 * 14. (Message<T>) -> Message<R>  -> functionKotlinMessageToMessage
 * 15. suspend (Message<T>) -> Message<R> -> functionKotlinSuspendMessageToMessage
 * 16. (Mono<Message<T>>) -> Mono<Message<R>> -> functionKotlinMonoMessageToMonoMessage
 * 17. (Flux<Message<T>>) -> Flux<Message<R>> -> functionKotlinFluxMessageToFluxMessage
 * 18. (Flow<Message<T>>) -> Flow<Message<R>> -> functionKotlinFlowMessageToFlowMessage
 * 19. suspend (Flow<Message<T>>) -> Flow<Message<R>> -> functionKotlinSuspendFlowMessageToFlowMessage
 *
 * @author Adrien Poupard
 */
class KotlinFunctionKotlinExamples

/** 1) (T) -> R */
@Component
open class FunctionKotlinPlainToPlain : (String) -> Int {
	override fun invoke(input: String): Int {
		return input.length
	}
}

/** 2) (T) -> Flow<R> */
@Component
class FunctionKotlinPlainToFlow : (String) -> Flow<String> {
	override fun invoke(input: String): Flow<String> {
		return flow {
			input.forEach { c -> emit(c.toString()) }
		}
	}
}

/** 3) (Flow<T>) -> R */
@Component
class FunctionKotlinFlowToPlain : (Flow<String>) -> Int {
	override fun invoke(flowInput: Flow<String>): Int {
		var count = 0
		runBlocking {
			flowInput.collect { count++ }
		}
		return count
	}
}

/** 4) (Flow<T>) -> Flow<R> */
@Component
class FunctionKotlinFlowToFlow : (Flow<Int>) -> Flow<String> {
	override fun invoke(flowInput: Flow<Int>): Flow<String> {
		return flowInput.map { it.toString() }
	}
}

/** 5) suspend (T) -> R */
@Component
class FunctionKotlinSuspendPlainToPlain : suspend (String) -> Int {
	override suspend fun invoke(input: String): Int {
		return input.length
	}
}

/** 6) suspend (T) -> Flow<R> */
@Component
class FunctionKotlinSuspendPlainToFlow : suspend (String) -> Flow<String> {
	override suspend fun invoke(input: String): Flow<String> {
		return flow {
			input.forEach { c -> emit(c.toString()) }
		}
	}
}

/** 7) suspend (Flow<T>) -> R */
@Component
class FunctionKotlinSuspendFlowToPlain : suspend (Flow<String>) -> Int {
	override suspend fun invoke(flowInput: Flow<String>): Int {
		var count = 0
		flowInput.collect { count++ }
		return count
	}
}

/** 8) suspend (Flow<T>) -> Flow<R> */
@Component
class FunctionKotlinSuspendFlowToFlow : suspend (Flow<String>) -> Flow<String> {
	override suspend fun invoke(incomingFlow: Flow<String>): Flow<String> {
		return flow {
			incomingFlow.collect { item -> emit(item.uppercase()) }
		}
	}
}

/** 9) (T) -> Mono<R> */
@Component
class FunctionKotlinPlainToMono : (String) -> Mono<Int> {
	override fun invoke(input: String): Mono<Int> {
		return Mono.just(input.length).delayElement(Duration.ofMillis(50))
	}
}

/** 10) (T) -> Flux<R> */
@Component
class FunctionKotlinPlainToFlux : (String) -> Flux<String> {
	override fun invoke(input: String): Flux<String> {
		return Flux.fromIterable(input.toList()).map { it.toString() }
	}
}

/** 11) (Mono<T>) -> Mono<R> */
@Component
class FunctionKotlinMonoToMono : (Mono<String>) -> Mono<String> {
	override fun invoke(monoInput: Mono<String>): Mono<String> {
		return monoInput.map { it.uppercase() }.delayElement(Duration.ofMillis(50))
	}
}

/** 12) (Flux<T>) -> Flux<R> */
@Component
class FunctionKotlinFluxToFlux : (Flux<String>) -> Flux<Int> {
	override fun invoke(fluxInput: Flux<String>): Flux<Int> {
		return fluxInput.map { it.length }
	}
}

/** 13) (Flux<T>) -> Mono<R> */
@Component
class FunctionKotlinFluxToMono : (Flux<String>) -> Mono<Int> {
	override fun invoke(fluxInput: Flux<String>): Mono<Int> {
		return fluxInput.count().map { it.toInt() }
	}
}

/** 14) (Message<T>) -> Message<R> */
@Component
class FunctionKotlinMessageToMessage : (Message<String>) -> Message<Int> {
	override fun invoke(message: Message<String>): Message<Int> {
		return MessageBuilder.withPayload(message.payload.length)
			.copyHeaders(message.headers)
			.setHeader("processed", "true")
			.build()
	}
}

/** 15) suspend (Message<T>) -> Message<R> */
@Component
class FunctionKotlinSuspendMessageToMessage : suspend (Message<String>) -> Message<Int> {
	override suspend fun invoke(message: Message<String>): Message<Int> {
		return MessageBuilder.withPayload(message.payload.length * 2)
			.copyHeaders(message.headers)
			.setHeader("suspend-processed", "true")
			.setHeader("original-id", message.headers["original-id"] ?: "N/A")
			.build()
	}
}

/** 16) (Mono<Message<T>>) -> Mono<Message<R>> */
@Component
class FunctionKotlinMonoMessageToMonoMessage : (Mono<Message<String>>) -> Mono<Message<Int>> {
	override fun invoke(monoMsgInput: Mono<Message<String>>): Mono<Message<Int>> {
		return monoMsgInput.map { message ->
			MessageBuilder.withPayload(message.payload.hashCode())
				.copyHeaders(message.headers)
				.setHeader("mono-processed", "true")
				.build()
		}
	}
}

/** 17) (Flux<Message<T>>) -> Flux<Message<R>> */
@Component
class FunctionKotlinFluxMessageToFluxMessage : (Flux<Message<String>>) -> Flux<Message<String>> {
	override fun invoke(fluxMsgInput: Flux<Message<String>>): Flux<Message<String>> {
		return fluxMsgInput.map { message ->
			MessageBuilder.withPayload(message.payload.uppercase())
				.copyHeaders(message.headers)
				.setHeader("flux-processed", "true")
				.build()
		}
	}
}

/** 18) (Flow<Message<T>>) -> Flow<Message<R>> */
@Component
class FunctionKotlinFlowMessageToFlowMessage : (Flow<Message<String>>) -> Flow<Message<String>> {
	override fun invoke(flowMsgInput: Flow<Message<String>>): Flow<Message<String>> {
		return flowMsgInput.map { message ->
			MessageBuilder.withPayload(message.payload.reversed())
				.copyHeaders(message.headers)
				.setHeader("flow-processed", "true")
				.build()
		}
	}
}

/** 19) suspend (Flow<Message<T>>) -> Flow<Message<R>> */
@Component
class FunctionKotlinSuspendFlowMessageToFlowMessage : suspend (Flow<Message<String>>) -> Flow<Message<String>> {
	override suspend fun invoke(flowMsgInput: Flow<Message<String>>): Flow<Message<String>> {
		return flow {
			flowMsgInput.collect { message ->
				emit(
					MessageBuilder.withPayload(message.payload.plus(" SUSPEND"))
						.copyHeaders(message.headers)
						.setHeader("suspend-flow-processed", "true")
						.build()
				)
			}
		}
	}
}

