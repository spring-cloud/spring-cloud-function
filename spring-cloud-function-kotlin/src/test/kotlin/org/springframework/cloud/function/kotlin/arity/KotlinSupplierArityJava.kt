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

import java.util.function.Supplier
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
 * Examples of implementing suppliers using Java's Supplier interface in Kotlin.
 * 
 * ## List of Combinations Implemented:
 * --- Coroutine ---
 * 1. Supplier<R>                      -> supplierJavaPlain
 * 2. Supplier<Flow<R>>               -> supplierJavaFlow
 * 3. Supplier<R> with suspend        -> supplierJavaSuspendPlain
 * 4. Supplier<Flow<R>> with suspend  -> supplierJavaSuspendFlow
 * --- Reactor ---
 * 5. Supplier<Mono<R>>               -> supplierJavaMono
 * 6. Supplier<Flux<R>>               -> supplierJavaFlux
 * --- Message<T> ---
 * 7. Supplier<Message<R>>            -> supplierJavaMessage
 * 8. Supplier<Mono<Message<R>>>     -> supplierJavaMonoMessage
 * 9. Supplier<Message<R>> with suspend -> supplierJavaSuspendMessage
 * 10. Supplier<Flux<Message<R>>>     -> supplierJavaFluxMessage
 * 11. Supplier<Flow<Message<R>>>     -> supplierJavaFlowMessage
 * 12. Supplier<Flow<Message<R>>> with suspend -> supplierJavaSuspendFlowMessage
 *
 * @author Adrien Poupard
 */
class KotlinSupplierJavaExamples

/** 1) Supplier<R> */
@Component
class SupplierJavaPlain : Supplier<Int> {
	override fun get(): Int {
		return 42
	}
}

/** 2) Supplier<Flow<R>> */
@Component
class SupplierJavaFlow : Supplier<Flow<String>> {
	override fun get(): Flow<String> {
		return flow {
			emit("A")
			emit("B")
			emit("C")
		}
	}
}



/** 5) Supplier<Mono<R>> */
@Component
class SupplierJavaMono : Supplier<Mono<String>> {
	override fun get(): Mono<String> {
		return Mono.just("Hello from Mono").delayElement(Duration.ofMillis(50))
	}
}

/** 6) Supplier<Flux<R>> */
@Component
class SupplierJavaFlux : Supplier<Flux<String>> {
	override fun get(): Flux<String> {
		return Flux.just("Alpha", "Beta", "Gamma").delayElements(Duration.ofMillis(20))
	}
}

/** 7) Supplier<Message<R>> */
@Component
class SupplierJavaMessage : Supplier<Message<String>> {
	override fun get(): Message<String> {
		return MessageBuilder.withPayload("Hello from Message")
			.setHeader("messageId", UUID.randomUUID().toString())
			.build()
	}
}

/** 8) Supplier<Mono<Message<R>>> */
@Component
class SupplierJavaMonoMessage : Supplier<Mono<Message<String>>> {
	override fun get(): Mono<Message<String>> {
		return Mono.just(
			MessageBuilder.withPayload("Hello from Mono Message")
				.setHeader("monoMessageId", UUID.randomUUID().toString())
				.setHeader("source", "mono")
				.build()
		).delayElement(Duration.ofMillis(40))
	}
}


/** 10) Supplier<Flux<Message<R>>> */
@Component
class SupplierJavaFluxMessage : Supplier<Flux<Message<String>>> {
	override fun get(): Flux<Message<String>> {
		return Flux.just("Msg1", "Msg2")
			.delayElements(Duration.ofMillis(30))
			.map { payload ->
				MessageBuilder.withPayload(payload)
					.setHeader("fluxMessageId", UUID.randomUUID().toString())
					.build()
			}
	}
}

/** 11) Supplier<Flow<Message<R>>> */
@Component
class SupplierJavaFlowMessage : Supplier<Flow<Message<String>>> {
	override fun get(): Flow<Message<String>> {
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
