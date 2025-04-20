package com.example.kotlin

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
 * 1. (T) -> Unit                 -> consumerSingle
 * 2. (Flow<T>) -> Unit           -> consumerFlow
 * 3. suspend (T) -> Unit         -> consumerSuspendSingle
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
 */
@Configuration
class KotlinConsumerExamples {

	/** 1) (T) -> Unit */
	@Bean
	fun consumerSingle(): (String) -> Unit = { input ->
		println("Consumed: $input")
	}

	/** 2) (Flow<T>) -> Unit */
	@Bean
	fun consumerFlow(): (Flow<String>) -> Unit = { flowInput ->
		println("Received flow: $flowInput (would collect in coroutine)")
	}

	/** 3) suspend (T) -> Unit */
	@Bean
	fun consumerSuspendSingle(): suspend (String) -> Unit = { input ->
		delay(100)
		println("Suspend consumed: $input")
	}

	/** 4) suspend (Flow<T>) -> Unit */
	@Bean
	fun consumerSuspendFlow(): suspend (Flow<String>) -> Unit = { flowInput ->
		flowInput.collect { item ->
			delay(50)
			println("Flow item consumed: $item")
		}
	}

	/** 5) (T) -> Mono<Void> */
	@Bean
	fun consumerMonoInput(): (String) -> Mono<Void> = { input ->
		Mono.fromRunnable<Void> {
			println("[Reactor] Consumed T: $input")
		}
	}

	/** 6) (Mono<T>) -> Mono<Void> */
	@Bean
	fun consumerMono(): (Mono<String>) -> Mono<Void> = { monoInput ->
		monoInput.doOnNext { item ->
			println("[Reactor] Consumed Mono item: $item")
		}.then()
	}

	/** 7) (Flux<T>) -> Mono<Void> */
	@Bean
	fun consumerFlux(): (Flux<String>) -> Mono<Void> = { fluxInput ->
		fluxInput.doOnNext { item ->
			println("[Reactor] Consumed Flux item: $item")
		}.then()
	}

	/** 8) (Message<T>) -> Unit */
	@Bean
	fun consumerMessage(): (Message<String>) -> Unit = { message ->
		println("[Message] Consumed payload: ${message.payload}, Headers: ${message.headers}")
	}

	/** 9) (Mono<Message<T>>) -> Mono<Void> */
	@Bean
	fun consumerMonoMessage(): (Mono<Message<String>>) -> Mono<Void> = { monoMsgInput ->
		monoMsgInput
			.doOnNext { message ->
				println("[Message][Mono] Consumed payload: ${message.payload}, Header id: ${message.headers.id}")
			}
			.then()
	}

	/** 10) suspend (Message<T>) -> Unit */
	@Bean
	fun consumerSuspendMessage(): suspend (Message<String>) -> Unit = { message ->
		delay(70)
		println("[Message][Suspend] Consumed payload: ${message.payload}, Header count: ${message.headers.size}")
	}

	/** 11) (Flux<Message<T>>) -> Unit */
	@Bean
	fun consumerFluxMessage(): (Flux<Message<String>>) -> Unit = { fluxMsgInput ->
		// Explicit subscription needed here because the lambda itself returns Unit
		fluxMsgInput.subscribe { message ->
			println("[Message] Consumed Flux payload: ${message.payload}, Headers: ${message.headers}")
		}
	}

	/** 12) (Flow<Message<T>>) -> Unit */
	@Bean
	fun consumerFlowMessage(): (Flow<Message<String>>) -> Unit = { flowMsgInput ->
		// Similar to Flux consumer returning Unit, explicit collection might be needed depending on context.
		println("[Message] Received Flow: $flowMsgInput (would need explicit collection if signature returns Unit)")
		// Example:
		// CoroutineScope(Dispatchers.IO).launch {
		//     flowMsgInput.collect { message -> println(...) }
		// }
	}

	/** 13) suspend (Flow<Message<T>>) -> Unit */
	@Bean
	fun consumerSuspendFlowMessage(): suspend (Flow<Message<String>>) -> Unit = { flowMsgInput ->
		flowMsgInput.collect { message ->
			delay(20)
			println("[Message] Consumed Suspend Flow payload: ${message.payload}, Headers: ${message.headers}")
		}
	}
}
