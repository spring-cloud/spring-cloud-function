package com.example.kotlin

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
 * 1. (T) -> R                     -> functionSingleToSingle
 * 2. (T) -> Flow<R>               -> functionSingleToFlow
 * 3. (Flow<T>) -> R               -> functionFlowToSingle
 * 4. (Flow<T>) -> Flow<R>         -> functionFlowToFlow
 * 5. suspend (T) -> R             -> functionSuspendSingleToSingle
 * 6. suspend (T) -> Flow<R>       -> functionSuspendSingleToFlow
 * 7. suspend (Flow<T>) -> R       -> functionSuspendFlowToSingle
 * 8. suspend (Flow<T>) -> Flow<R> -> functionSuspendFlowToFlow
 * --- Reactor ---
 * 9. (T) -> Mono<R>               -> functionSingleToMono
 * 10. (T) -> Flux<R>              -> functionSingleToFlux
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
 */
@Configuration
class KotlinFunctionExamples {

	/** 1) (T) -> R */
	@Bean
	fun functionSingleToSingle(): (String) -> Int = { input ->
		input.length
	}

	/** 2) (T) -> Flow<R> */
	@Bean
	fun functionSingleToFlow(): (String) -> Flow<String> = { input ->
		flow {
			input.forEach { c -> emit(c.toString()) }
		}
	}

	/** 3) (Flow<T>) -> R */
	@Bean
	fun functionFlowToSingle(): (Flow<String>) -> Int = { flowInput ->
		var count = 0
		runBlocking {
			flowInput.collect { count++ }
		}
		count
	}

	/** 4) (Flow<T>) -> Flow<R> */
	@Bean
	fun functionFlowToFlow(): (Flow<Int>) -> Flow<String> = { flowInput ->
		flowInput.map { it.toString() }
	}

	/** 5) suspend (T) -> R */
	@Bean
	fun functionSuspendSingleToSingle(): suspend (String) -> Int = { input ->
		delay(100)
		input.length
	}

	/** 6) suspend (T) -> Flow<R> */
	@Bean
	fun functionSuspendSingleToFlow(): suspend (String) -> Flow<String> = { input ->
		flow {
			delay(100)
			input.forEach { c -> emit(c.toString()) }
		}
	}

	/** 7) suspend (Flow<T>) -> R */
	@Bean
	fun functionSuspendFlowToSingle(): suspend (Flow<String>) -> Int = { flowInput ->
		var count = 0
		flowInput.collect { count++ }
		count
	}

	/** 8) suspend (Flow<T>) -> Flow<R> */
	@Bean
	fun functionSuspendFlowToFlow(): suspend (Flow<String>) -> Flow<String> = { incomingFlow ->
		flow {
			delay(100)
			incomingFlow.collect { item -> emit(item.uppercase()) }
		}
	}

	/** 9) (T) -> Mono<R> */
	@Bean
	fun functionSingleToMono(): (String) -> Mono<Int> = { input ->
		Mono.just(input.length).delayElement(Duration.ofMillis(50))
	}

	/** 10) (T) -> Flux<R> */
	@Bean
	fun functionSingleToFlux(): (String) -> Flux<String> = { input ->
		Flux.fromIterable(input.toList()).map { it.toString() }
	}

	/** 11) (Mono<T>) -> Mono<R> */
	@Bean
	fun functionMonoToMono(): (Mono<String>) -> Mono<String> = { monoInput ->
		monoInput.map { it.uppercase() }.delayElement(Duration.ofMillis(50))
	}

	/** 12) (Flux<T>) -> Flux<R> */
	@Bean
	fun functionFluxToFlux(): (Flux<String>) -> Flux<Int> = { fluxInput ->
		fluxInput.map { it.length }
	}

	/** 13) (Flux<T>) -> Mono<R> */
	@Bean
	fun functionFluxToMono(): (Flux<String>) -> Mono<Int> = { fluxInput ->
		fluxInput.count().map { it.toInt() }
	}

	/** 14) (Message<T>) -> Message<R> */
	@Bean
	fun functionMessageToMessage(): (Message<String>) -> Message<Int> = { message ->
		MessageBuilder.withPayload(message.payload.length)
			.copyHeaders(message.headers)
			.setHeader("processed", "true")
			.build()
	}

	/** 15) suspend (Message<T>) -> Message<R> */
	@Bean
	fun functionSuspendMessageToMessage(): suspend (Message<String>) -> Message<Int> = { message ->
		delay(50)
		MessageBuilder.withPayload(message.payload.length * 2)
			.copyHeaders(message.headers)
			.setHeader("suspend-processed", "true")
			.setHeader("original-id", message.headers.id ?: "N/A")
			.build()
	}

	/** 16) (Mono<Message<T>>) -> Mono<Message<R>> */
	@Bean
	fun functionMonoMessageToMonoMessage(): (Mono<Message<String>>) -> Mono<Message<Int>> = { monoMsgInput ->
		monoMsgInput.map { message ->
			MessageBuilder.withPayload(message.payload.hashCode())
				.copyHeaders(message.headers)
				.setHeader("mono-processed", "true")
				.build()
		}
	}

	/** 17) (Flux<Message<T>>) -> Flux<Message<R>> */
	@Bean
	fun functionFluxMessageToFluxMessage(): (Flux<Message<String>>) -> Flux<Message<String>> = { fluxMsgInput ->
		fluxMsgInput.map { message ->
			MessageBuilder.withPayload(message.payload.uppercase())
				.copyHeaders(message.headers)
				.setHeader("flux-processed", "true")
				.build()
		}
	}

	/** 18) (Flow<Message<T>>) -> Flow<Message<R>> */
	@Bean
	fun functionFlowMessageToFlowMessage(): (Flow<Message<String>>) -> Flow<Message<String>> = { flowMsgInput ->
		flowMsgInput.map { message ->
			MessageBuilder.withPayload(message.payload.reversed())
				.copyHeaders(message.headers)
				.setHeader("flow-processed", "true")
				.build()
		}
	}

	/** 19) suspend (Flow<Message<T>>) -> Flow<Message<R>> */
	@Bean
	fun functionSuspendFlowMessageToFlowMessage(): suspend (Flow<Message<String>>) -> Flow<Message<String>> = { flowMsgInput ->
		flow {
			flowMsgInput.collect { message ->
				delay(20)
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
