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

import java.util.function.Consumer
import kotlinx.coroutines.flow.Flow
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Examples of implementing consumers using Java's Consumer interface in Kotlin.
 * 
 * ## List of Combinations Implemented:
 * --- Coroutine ---
 * 1. Consumer<T>                     -> consumerJavaPlain
 * 2. Consumer<Flow<T>>               -> consumerJavaFlow
 * 3. Consumer<T> with suspend        -> consumerJavaSuspendPlain
 * 4. Consumer<Flow<T>> with suspend  -> consumerJavaSuspendFlow
 * --- Reactor ---
 * 5. Consumer<T> returning Mono<Void> -> consumerJavaMonoInput
 * 6. Consumer<Mono<T>>                -> consumerJavaMono
 * 7. Consumer<Flux<T>>                -> consumerJavaFlux
 * --- Message<T> ---
 * 8. Consumer<Message<T>>             -> consumerJavaMessage
 * 9. Consumer<Mono<Message<T>>>       -> consumerJavaMonoMessage
 * 10. Consumer<Message<T>> with suspend -> consumerJavaSuspendMessage
 * 11. Consumer<Flux<Message<T>>>      -> consumerJavaFluxMessage
 * 12. Consumer<Flow<Message<T>>>      -> consumerJavaFlowMessage
 * 13. Consumer<Flow<Message<T>>> with suspend -> consumerJavaSuspendFlowMessage
 *
 * @author Adrien Poupard
 */
class KotlinConsumerJavaExamples

/** 1) Consumer<T> */
@Component
class ConsumerJavaPlain : Consumer<String> {
	override fun accept(input: String) {
		println("Consumed: $input")
	}
}

/** 2) Consumer<Flow<T>> */
@Component
class ConsumerJavaFlow : Consumer<Flow<String>> {
	override fun accept(flowInput: Flow<String>) {
		println("Received flow: $flowInput (would collect in coroutine)")
	}
}



/** 5) Consumer<T> returning Mono<Void> */
@Component
class ConsumerJavaMonoInput : Consumer<String> {
	override fun accept(input: String) {
		println("[Reactor] Consumed T: $input")
		// Note: Consumer doesn't return anything, but we're simulating the Mono<Void> behavior
	}
}

/** 6) Consumer<Mono<T>> */
@Component
class ConsumerJavaMono : Consumer<Mono<String>> {
	override fun accept(monoInput: Mono<String>) {
		monoInput.subscribe { item ->
			println("[Reactor] Consumed Mono item: $item")
		}
	}
}

/** 7) Consumer<Flux<T>> */
@Component
class ConsumerJavaFlux : Consumer<Flux<String>> {
	override fun accept(fluxInput: Flux<String>) {
		fluxInput.subscribe { item ->
			println("[Reactor] Consumed Flux item: $item")
		}
	}
}

/** 8) Consumer<Message<T>> */
@Component
class ConsumerJavaMessage : Consumer<Message<String>> {
	override fun accept(message: Message<String>) {
		println("[Message] Consumed payload: ${message.payload}, Headers: ${message.headers}")
	}
}

/** 9) Consumer<Mono<Message<T>>> */
@Component
class ConsumerJavaMonoMessage : Consumer<Mono<Message<String>>> {
	override fun accept(monoMsgInput: Mono<Message<String>>) {
		monoMsgInput.subscribe { message ->
			println("[Message][Mono] Consumed payload: ${message.payload}, Header id: ${message.headers.id}")
		}
	}
}


/** 11) Consumer<Flux<Message<T>>> */
@Component
class ConsumerJavaFluxMessage : Consumer<Flux<Message<String>>> {
	override fun accept(fluxMsgInput: Flux<Message<String>>) {
		fluxMsgInput.subscribe { message ->
			println("[Message] Consumed Flux payload: ${message.payload}, Headers: ${message.headers}")
		}
	}
}

/** 12) Consumer<Flow<Message<T>>> */
@Component
class ConsumerJavaFlowMessage : Consumer<Flow<Message<String>>> {
	override fun accept(flowMsgInput: Flow<Message<String>>) {
		println("[Message] Received Flow: $flowMsgInput (would need explicit collection)")
	}
}

//}
