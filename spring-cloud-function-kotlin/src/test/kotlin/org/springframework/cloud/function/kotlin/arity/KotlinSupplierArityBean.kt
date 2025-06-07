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
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.UUID

/**
 * ## List of Combinations Tested (in requested order):
 * --- Coroutine ---
 * 1. () -> R                      -> supplierPlain
 * 2. () -> Flow<R>               -> supplierFlow
 * 3. suspend () -> R             -> supplierSuspendPlain
 * 4. suspend () -> Flow<R>       -> supplierSuspendFlow
 * --- Reactor ---
 * 5. () -> Mono<R>               -> supplierMono
 * 6. () -> Flux<R>               -> supplierFlux
 * --- Message<T> ---
 * 7. () -> Message<R>            -> supplierMessage
 * 8. () -> Mono<Message<R>>     -> supplierMonoMessage
 * 9. suspend () -> Message<R>    -> supplierSuspendMessage
 * 10. () -> Flux<Message<R>>     -> supplierFluxMessage
 * 11. () -> Flow<Message<R>>     -> supplierFlowMessage
 * 12. suspend () -> Flow<Message<R>> -> supplierSuspendFlowMessage
 *
 * @author Adrien Poupard
 */
@Configuration
open class KotlinSupplierArityBean {

	/** 1) () -> R */
	@Bean
	open fun supplierPlain(): () -> Int = {
		42
	}

	/** 2) () -> Flow<R> */
	@Bean
	open fun supplierFlow(): () -> Flow<String> = {
		flow {
			emit("A")
			emit("B")
			emit("C")
		}
	}

	/** 3) suspend () -> R */
	@Bean
	open fun supplierSuspendPlain(): suspend () -> String = {
		"Hello from suspend"
	}

	/** 4) suspend () -> Flow<R> */
	@Bean
	open fun supplierSuspendFlow(): suspend () -> Flow<String> = {
		flow {
			emit("x")
			emit("y")
			emit("z")
		}
	}

	/** 5) () -> Mono<R> */
	@Bean
	open fun supplierMono(): () -> Mono<String> = {
		Mono.just("Hello from Mono").delayElement(Duration.ofMillis(50))
	}

	/** 6) () -> Flux<R> */
	@Bean
	open fun supplierFlux(): () -> Flux<String> = {
		Flux.just("Alpha", "Beta", "Gamma").delayElements(Duration.ofMillis(20))
	}

	/** 7) () -> Message<R> */
	@Bean
	open fun supplierMessage(): () -> Message<String> = {
		MessageBuilder.withPayload("Hello from Message")
			.setHeader("messageId", UUID.randomUUID().toString())
			.build()
	}

	/** 8) () -> Mono<Message<R>> */
	@Bean
	open fun supplierMonoMessage(): () -> Mono<Message<String>> = {
		Mono.just(
			MessageBuilder.withPayload("Hello from Mono Message")
				.setHeader("monoMessageId", UUID.randomUUID().toString())
				.setHeader("source", "mono")
				.build()
		).delayElement(Duration.ofMillis(40))
	}

	/** 9) suspend () -> Message<R> */
	@Bean
	open fun supplierSuspendMessage(): suspend () -> Message<String> = {
		MessageBuilder.withPayload("Hello from Suspend Message")
			.setHeader("suspendMessageId", UUID.randomUUID().toString())
			.setHeader("wasSuspended", true)
			.build()
	}

	/** 10) () -> Flux<Message<R>> */
	@Bean
	open fun supplierFluxMessage(): () -> Flux<Message<String>> = {
		Flux.just("Msg1", "Msg2")
			.delayElements(Duration.ofMillis(30))
			.map { payload ->
				MessageBuilder.withPayload(payload)
					.setHeader("fluxMessageId", UUID.randomUUID().toString())
					.build()
			}
	}

	/** 11) () -> Flow<Message<R>> */
	@Bean
	open fun supplierFlowMessage(): () -> Flow<Message<String>> = {
		flow {
			listOf("FlowMsg1", "FlowMsg2").forEach { payload ->
				emit(
					MessageBuilder.withPayload(payload)
						.setHeader("flowMessageId", UUID.randomUUID().toString())
						.build()
				)
			}
		}
	}

	/** 12) suspend () -> Flow<Message<R>> */
	@Bean
	open fun supplierSuspendFlowMessage(): suspend () -> Flow<Message<String>> = {
		flow {
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
