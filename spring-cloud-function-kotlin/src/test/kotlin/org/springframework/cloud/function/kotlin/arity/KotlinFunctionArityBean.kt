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
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * ## List of Combinations Tested (in requested order):
 * --- Coroutine ---
 * 1. (T) -> R                     -> functionPlainToPlain
 * 2. (T) -> Flow<R>               -> functionPlainToFlow
 * 3. (Flow<T>) -> R               -> functionFlowToPlain
 * 4. (Flow<T>) -> Flow<R>         -> functionFlowToFlow
 * 5. suspend (T) -> R             -> functionSuspendPlainToPlain
 * 6. suspend (T) -> Flow<R>       -> functionSuspendPlainToFlow
 * 7. suspend (Flow<T>) -> R       -> functionSuspendFlowToPlain
 * 8. suspend (Flow<T>) -> Flow<R> -> functionSuspendFlowToFlow
 * --- Reactor ---
 * 9. (T) -> Mono<R>               -> functionPlainToMono
 * 10. (T) -> Flux<R>              -> functionPlainToFlux
 * 11. (Mono<T>) -> Mono<R>        -> functionMonoToMono
 * 12. (Flux<T>) -> Flux<R>        -> functionFluxToFlux
 * 13. (Flux<T>) -> Mono<R>        -> functionFluxToMono
 * --- Message<T> ---
 * 14. (Message<T>) -> Message<R>  -> functionMessageToMessage
 * 15. suspend (Message<T>) -> Message<R> -> functionSuspendMessageToMessage
 * 16. (Mono<Message<T>>) -> Mono<Message<R>> -> functionMonoMessageToMonoMessage
 * 17. (Flux<Message<T>>) -> Flux<Message<R>> -> functionFluxMessageToFluxMessage
 * 18. (Flow<Message<T>>) -> Flow<Message<R>> -> functionFlowMessageToFlowMessage
 * 19. suspend (Flow<Message<T>>) -> Flow<Message<R>> -> functionSuspendFlowMessageToFlowMessage
 *
 * @author Adrien Poupard
 */
@Configuration
open class KotlinFunctionArityBean {

	/** 1) (T) -> R */
	@Bean
	open fun functionPlainToPlain(): (String) -> Int = { input ->
		input.length
	}

	/** 2) (T) -> Flow<R> */
	@Bean
	open fun functionPlainToFlow(): (String) -> Flow<String> = { input ->
		flow {
			input.forEach { c -> emit(c.toString()) }
		}
	}

	/** 3) (Flow<T>) -> R */
	@Bean
	open fun functionFlowToPlain(): (Flow<String>) -> Int = { flowInput ->
		var count = 0
		runBlocking {
			flowInput.collect { count++ }
		}
		count
	}

	/** 4) (Flow<T>) -> Flow<R> */
	@Bean
	open fun functionFlowToFlow(): (Flow<Int>) -> Flow<String> = { flowInput ->
		flowInput.map { it.toString() }
	}

	/** 5) suspend (T) -> R */
	@Bean
	open fun functionSuspendPlainToPlain(): suspend (String) -> Int = { input ->
		input.length
	}

	/** 6) suspend (T) -> Flow<R> */
	@Bean
	open fun functionSuspendPlainToFlow(): suspend (String) -> Flow<String> = { input ->
		flow {
			input.forEach { c -> emit(c.toString()) }
		}
	}

	/** 7) suspend (Flow<T>) -> R */
	@Bean
	open fun functionSuspendFlowToPlain(): suspend (Flow<String>) -> Int = { flowInput ->
		var count = 0
		flowInput.collect { count++ }
		count
	}

	/** 8) suspend (Flow<T>) -> Flow<R> */
	@Bean
	open fun functionSuspendFlowToFlow(): suspend (Flow<String>) -> Flow<String> = { incomingFlow ->
		flow {
			incomingFlow.collect { item -> emit(item.uppercase()) }
		}
	}

	/** 9) (T) -> Mono<R> */
	@Bean
	open fun functionPlainToMono(): (String) -> Mono<Int> = { input ->
		Mono.just(input.length).delayElement(Duration.ofMillis(50))
	}

	/** 10) (T) -> Flux<R> */
	@Bean
	open fun functionPlainToFlux(): (String) -> Flux<String> = { input ->
		Flux.fromIterable(input.toList()).map { it.toString() }
	}

	/** 11) (Mono<T>) -> Mono<R> */
	@Bean
	open fun functionMonoToMono(): (Mono<String>) -> Mono<String> = { monoInput ->
		monoInput.map { it.uppercase() }.delayElement(Duration.ofMillis(50))
	}

	/** 12) (Flux<T>) -> Flux<R> */
	@Bean
	open fun functionFluxToFlux(): (Flux<String>) -> Flux<Int> = { fluxInput ->
		fluxInput.map { it.length }
	}

	/** 13) (Flux<T>) -> Mono<R> */
	@Bean
	open fun functionFluxToMono(): (Flux<String>) -> Mono<Int> = { fluxInput ->
		fluxInput.count().map { it.toInt() }
	}

	/** 14) (Message<T>) -> Message<R> */
	@Bean
	open fun functionMessageToMessage(): (Message<String>) -> Message<Int> = { message ->
		MessageBuilder.withPayload(message.payload.length)
			.copyHeaders(message.headers)
			.setHeader("processed", "true")
			.build()
	}

	/** 15) suspend (Message<T>) -> Message<R> */
	@Bean
	open fun functionSuspendMessageToMessage(): suspend (Message<String>) -> Message<Int> = { message ->
		MessageBuilder.withPayload(message.payload.length * 2)
			.copyHeaders(message.headers)
			.setHeader("suspend-processed", "true")
			.setHeader("original-id", message.headers["original-id"] ?: "N/A")
			.build()
	}

	/** 16) (Mono<Message<T>>) -> Mono<Message<R>> */
	@Bean
	open fun functionMonoMessageToMonoMessage(): (Mono<Message<String>>) -> Mono<Message<Int>> = { monoMsgInput ->
		monoMsgInput.map { message ->
			MessageBuilder.withPayload(message.payload.hashCode())
				.copyHeaders(message.headers)
				.setHeader("mono-processed", "true")
				.build()
		}
	}

	/** 17) (Flux<Message<T>>) -> Flux<Message<R>> */
	@Bean
	open fun functionFluxMessageToFluxMessage(): (Flux<Message<String>>) -> Flux<Message<String>> = { fluxMsgInput ->
		fluxMsgInput.map { message ->
			MessageBuilder.withPayload(message.payload.uppercase())
				.copyHeaders(message.headers)
				.setHeader("flux-processed", "true")
				.build()
		}
	}

	/** 18) (Flow<Message<T>>) -> Flow<Message<R>> */
	@Bean
	open fun functionFlowMessageToFlowMessage(): (Flow<Message<String>>) -> Flow<Message<String>> = { flowMsgInput ->
		flowMsgInput.map { message ->
			MessageBuilder.withPayload(message.payload.reversed())
				.copyHeaders(message.headers)
				.setHeader("flow-processed", "true")
				.build()
		}
	}

	/** 19) suspend (Flow<Message<T>>) -> Flow<Message<R>> */
	@Bean
	open fun functionSuspendFlowMessageToFlowMessage(): suspend (Flow<Message<String>>) -> Flow<Message<String>> = { flowMsgInput ->
		flow {
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
