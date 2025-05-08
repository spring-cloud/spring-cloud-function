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
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * ## List of Combinations Tested (in requested order):
 * --- Coroutine ---
 * 1. (T) -> Unit                 -> consumerPlain
 * 2. (Flow<T>) -> Unit           -> consumerFlow
 * 3. suspend (T) -> Unit         -> consumerSuspendPlain
 * 4. suspend (Flow<T>) -> Unit   -> consumerSuspendFlow
 * --- Reactor ---
 * 5. (T) -> Mono<Void>           -> consumerMonoInput
 * 6. (Mono<T>) -> Mono<Void>     -> consumerMono
 * 7. (Flux<T>) -> Mono<Void>     -> consumerFlux
 * --- Message<T> ---
 * 8. (Message<T>) -> Unit        -> consumerMessage
 * 9. (Mono<Message<T>>) -> Mono<Void> -> consumerMonoMessage
 * 10. suspend (Message<T>) -> Unit -> consumerSuspendMessage
 * 11. (Flux<Message<T>>) -> Unit  -> consumerFluxMessage
 * 12. (Flow<Message<T>>) -> Unit  -> consumerFlowMessage
 * 13. suspend (Flow<Message<T>>) -> Unit -> consumerSuspendFlowMessage
 *
 *  @author Adrien Poupard
 */
@Configuration
open class KotlinConsumerArityBean {

	/** 1) (T) -> Unit */
	@Bean
	open fun consumerPlain(): (String) -> Unit = { input ->
		println("Consumed: $input")
	}

	/** 2) (Flow<T>) -> Unit */
	@Bean
	open fun consumerFlow(): (Flow<String>) -> Unit = { flowInput ->
		println("Received flow: $flowInput (would collect in coroutine)")
	}

	/** 3) suspend (T) -> Unit */
	@Bean
	open fun consumerSuspendPlain(): suspend (String) -> Unit = { input ->
		println("Suspend consumed: $input")
	}

	/** 4) suspend (Flow<T>) -> Unit */
	@Bean
	open fun consumerSuspendFlow(): suspend (Flow<String>) -> Unit = { flowInput ->
		flowInput.collect { item ->
			println("Flow item consumed: $item")
		}
	}

	/** 5) (T) -> Mono<Void> */
	@Bean
	open fun consumerMonoInput(): (String) -> Mono<Void> = { input ->
		Mono.fromRunnable<Void> {
			println("[Reactor] Consumed T: $input")
		}
	}

	/** 6) (Mono<T>) -> Mono<Void> */
	@Bean
	open fun consumerMono(): (Mono<String>) -> Mono<Void> = { monoInput ->
		monoInput.doOnNext { item ->
			println("[Reactor] Consumed Mono item: $item")
		}.then()
	}

	/** 7) (Flux<T>) -> Mono<Void> */
	@Bean
	open fun consumerFlux(): (Flux<String>) -> Mono<Void> = { fluxInput ->
		fluxInput.doOnNext { item ->
			println("[Reactor] Consumed Flux item: $item")
		}.then()
	}

	/** 8) (Message<T>) -> Unit */
	@Bean
	open fun consumerMessage(): (Message<String>) -> Unit = { message ->
		println("[Message] Consumed payload: ${message.payload}, Headers: ${message.headers}")
	}

	/** 9) (Mono<Message<T>>) -> Mono<Void> */
	@Bean
	open fun consumerMonoMessage(): (Mono<Message<String>>) -> Mono<Void> = { monoMsgInput ->
		monoMsgInput
			.doOnNext { message ->
				println("[Message][Mono] Consumed payload: ${message.payload}, Header id: ${message.headers.id}")
			}
			.then()
	}

	/** 10) suspend (Message<T>) -> Unit */
	@Bean
	open fun consumerSuspendMessage(): suspend (Message<String>) -> Unit = { message ->
		println("[Message][Suspend] Consumed payload: ${message.payload}, Header count: ${message.headers.size}")
	}

	/** 11) (Flux<Message<T>>) -> Unit */
	@Bean
	open fun consumerFluxMessage(): (Flux<Message<String>>) -> Unit = { fluxMsgInput ->
		// Explicit subscription needed here because the lambda itself returns Unit
		fluxMsgInput.subscribe { message ->
			println("[Message] Consumed Flux payload: ${message.payload}, Headers: ${message.headers}")
		}
	}

	/** 12) (Flow<Message<T>>) -> Unit */
	@Bean
	open fun consumerFlowMessage(): (Flow<Message<String>>) -> Unit = { flowMsgInput ->
		// Similar to Flux consumer returning Unit, explicit collection might be needed depending on context.
		println("[Message] Received Flow: $flowMsgInput (would need explicit collection if signature returns Unit)")
		// Example:
		// CoroutineScope(Dispatchers.IO).launch {
		//     flowMsgInput.collect { message -> println(...) }
		// }
	}

	/** 13) suspend (Flow<Message<T>>) -> Unit */
	@Bean
	open fun consumerSuspendFlowMessage(): suspend (Flow<Message<String>>) -> Unit = { flowMsgInput ->
		flowMsgInput.collect { message ->
			println("[Message] Consumed Suspend Flow payload: ${message.payload}, Headers: ${message.headers}")
		}
	}
}
