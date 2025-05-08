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

import java.util.function.Function
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
 * Examples of implementing functions using Java's Function interface in Kotlin.
 * 
 * ## List of Combinations Implemented:
 * --- Coroutine ---
 * 1. Function<T, R>                     -> functionJavaPlainToPlain
 * 2. Function<T, Flow<R>>               -> functionJavaPlainToFlow
 * 3. Function<Flow<T>, R>               -> functionJavaFlowToPlain
 * 4. Function<Flow<T>, Flow<R>>         -> functionJavaFlowToFlow
 * 5. Function<T, R> with suspend        -> functionJavaSuspendPlainToPlain
 * 6. Function<T, Flow<R>> with suspend  -> functionJavaSuspendPlainToFlow
 * 7. Function<Flow<T>, R> with suspend  -> functionJavaSuspendFlowToPlain
 * 8. Function<Flow<T>, Flow<R>> with suspend -> functionJavaSuspendFlowToFlow
 * --- Reactor ---
 * 9. Function<T, Mono<R>>               -> functionJavaPlainToMono
 * 10. Function<T, Flux<R>>              -> functionJavaPlainToFlux
 * 11. Function<Mono<T>, Mono<R>>        -> functionJavaMonoToMono
 * 12. Function<Flux<T>, Flux<R>>        -> functionJavaFluxToFlux
 * 13. Function<Flux<T>, Mono<R>>        -> functionJavaFluxToMono
 * --- Message<T> ---
 * 14. Function<Message<T>, Message<R>>  -> functionJavaMessageToMessage
 * 15. Function<Message<T>, Message<R>> with suspend -> functionJavaSuspendMessageToMessage
 * 16. Function<Mono<Message<T>>, Mono<Message<R>>> -> functionJavaMonoMessageToMonoMessage
 * 17. Function<Flux<Message<T>>, Flux<Message<R>>> -> functionJavaFluxMessageToFluxMessage
 * 18. Function<Flow<Message<T>>, Flow<Message<R>>> -> functionJavaFlowMessageToFlowMessage
 * 19. Function<Flow<Message<T>>, Flow<Message<R>>> with suspend -> functionJavaSuspendFlowMessageToFlowMessage
 *
 * @author Adrien Poupard
 */
class KotlinFunctionJavaExamples

/** 1) Function<T, R> */
@Component
class FunctionJavaPlainToPlain : Function<String, Int> {
	override fun apply(input: String): Int {
		return input.length
	}
}

/** 2) Function<T, Flow<R>> */
@Component
class FunctionJavaPlainToFlow : Function<String, Flow<String>> {
	override fun apply(input: String): Flow<String> {
		return flow {
			input.forEach { c -> emit(c.toString()) }
		}
	}
}

/** 3) Function<Flow<T>, R> */
@Component
class FunctionJavaFlowToPlain : Function<Flow<String>, Int> {
	override fun apply(flowInput: Flow<String>): Int {
		var count = 0
		runBlocking {
			flowInput.collect { count++ }
		}
		return count
	}
}

/** 4) Function<Flow<T>, Flow<R>> */
@Component
class FunctionJavaFlowToFlow : Function<Flow<Int>, Flow<String>> {
	override fun apply(flowInput: Flow<Int>): Flow<String> {
		return flowInput.map { it.toString() }
	}
}





/** 9) Function<T, Mono<R>> */
@Component
class FunctionJavaPlainToMono : Function<String, Mono<Int>> {
	override fun apply(input: String): Mono<Int> {
		return Mono.just(input.length).delayElement(Duration.ofMillis(50))
	}
}

/** 10) Function<T, Flux<R>> */
@Component
class FunctionJavaPlainToFlux : Function<String, Flux<String>> {
	override fun apply(input: String): Flux<String> {
		return Flux.fromIterable(input.toList()).map { it.toString() }
	}
}

/** 11) Function<Mono<T>, Mono<R>> */
@Component
class FunctionJavaMonoToMono : Function<Mono<String>, Mono<String>> {
	override fun apply(monoInput: Mono<String>): Mono<String> {
		return monoInput.map { it.uppercase() }.delayElement(Duration.ofMillis(50))
	}
}

/** 12) Function<Flux<T>, Flux<R>> */
@Component
class FunctionJavaFluxToFlux : Function<Flux<String>, Flux<Int>> {
	override fun apply(fluxInput: Flux<String>): Flux<Int> {
		return fluxInput.map { it.length }
	}
}

/** 13) Function<Flux<T>, Mono<R>> */
@Component
class FunctionJavaFluxToMono : Function<Flux<String>, Mono<Int>> {
	override fun apply(fluxInput: Flux<String>): Mono<Int> {
		return fluxInput.count().map { it.toInt() }
	}
}

/** 14) Function<Message<T>, Message<R>> */
@Component
class FunctionJavaMessageToMessage : Function<Message<String>, Message<Int>> {
	override fun apply(message: Message<String>): Message<Int> {
		return MessageBuilder.withPayload(message.payload.length)
			.copyHeaders(message.headers)
			.setHeader("processed", "true")
			.build()
	}
}


/** 16) Function<Mono<Message<T>>, Mono<Message<R>>> */
@Component
class FunctionJavaMonoMessageToMonoMessage : Function<Mono<Message<String>>, Mono<Message<Int>>> {
	override fun apply(monoMsgInput: Mono<Message<String>>): Mono<Message<Int>> {
		return monoMsgInput.map { message ->
			MessageBuilder.withPayload(message.payload.hashCode())
				.copyHeaders(message.headers)
				.setHeader("mono-processed", "true")
				.build()
		}
	}
}

/** 17) Function<Flux<Message<T>>, Flux<Message<R>>> */
@Component
class FunctionJavaFluxMessageToFluxMessage : Function<Flux<Message<String>>, Flux<Message<String>>> {
	override fun apply(fluxMsgInput: Flux<Message<String>>): Flux<Message<String>> {
		return fluxMsgInput.map { message ->
			MessageBuilder.withPayload(message.payload.uppercase())
				.copyHeaders(message.headers)
				.setHeader("flux-processed", "true")
				.build()
		}
	}
}

/** 18) Function<Flow<Message<T>>, Flow<Message<R>>> */
@Component
class FunctionJavaFlowMessageToFlowMessage : Function<Flow<Message<String>>, Flow<Message<String>>> {
	override fun apply(flowMsgInput: Flow<Message<String>>): Flow<Message<String>> {
		return flowMsgInput.map { message ->
			MessageBuilder.withPayload(message.payload.reversed())
				.copyHeaders(message.headers)
				.setHeader("flow-processed", "true")
				.build()
		}
	}
}
