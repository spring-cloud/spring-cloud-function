package com.example.kotlin

import java.time.Duration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.cloud.function.context.test.FunctionalSpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Test class for verifying the Kotlin Consumer examples in [KotlinConsumerExamples].
 * Each bean is exposed at "/{beanName}" by Spring Cloud Function.
 *
 * ## Consumers Tested:
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
@FunctionalSpringBootTest
@AutoConfigureWebTestClient
class KotlinConsumerExamplesTest {

	@Autowired
	lateinit var webTestClient: WebTestClient

	@BeforeEach
	fun setup() {
		this.webTestClient = webTestClient.mutate()
			.responseTimeout(Duration.ofSeconds(120))
			.build()
	}

	/**
	 * 1. (T) -> Unit -> consumerSingle
	 * Takes a String (side-effect only).
	 *
	 * --- Input: ---
	 * POST /consumerSingle
	 * "Log me"
	 *
	 * --- Output: ---
	 * 202 ACCEPTED
	 * (No body)
	 */
	@Test
	fun testConsumerSingle() {
		webTestClient.post()
			.uri("/consumerSingle")
			.bodyValue("Log me")
			.exchange()
			.expectStatus().isAccepted
	}

	/**
	 * 2. (Flow<T>) -> Unit -> consumerFlow
	 * Takes a Flow of Strings (side-effect only).
	 *
	 * --- Input: ---
	 * POST /consumerFlow
	 * Content-Type: application/json
	 * ["one","two"]
	 *
	 * --- Output: ---
	 * 202 ACCEPTED
	 * (No body)
	 */
	@Test
	fun testConsumerFlow() {
		webTestClient.post()
			.uri("/consumerFlow")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("one", "two"))
			.exchange()
			.expectStatus().isAccepted
	}

	/**
	 * 3. suspend (T) -> Unit -> consumerSuspendSingle
	 * Suspending consumer that takes a String (side-effect only).
	 *
	 * --- Input: ---
	 * POST /consumerSuspendSingle
	 * "test"
	 *
	 * --- Output: ---
	 * 202 ACCEPTED
	 * (No body)
	 */
	@Test
	fun testConsumerSuspendSingle() {
		webTestClient.post()
			.uri("/consumerSuspendSingle")
			.bodyValue("test")
			.exchange()
			.expectStatus().isAccepted
	}

	/**
	 * 4. suspend (Flow<T>) -> Unit -> consumerSuspendFlow
	 * Suspending consumer that takes a Flow of Strings (side-effect only).
	 *
	 * --- Input: ---
	 * POST /consumerSuspendFlow
	 * Content-Type: application/json
	 * ["foo","bar"]
	 *
	 * --- Output: ---
	 * 202 ACCEPTED
	 * (No body)
	 */
	@Test
	fun testConsumerSuspendFlow() {
		webTestClient.post()
			.uri("/consumerSuspendFlow")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("foo", "bar"))
			.exchange()
			.expectStatus().isAccepted
	}


	/**
	 * 5. (T) -> Mono<Void> -> consumerMonoInput
	 * Consumer takes String input, returns Mono<Void>.
	 *
	 * --- Input: ---
	 * POST /consumerMonoInput
	 * "Consume Me (Mono Input)"
	 *
	 * --- Output: ---
	 * 202 ACCEPTED
	 * (No body)
	 */
	@Test
	fun testConsumerMonoInput() {
		webTestClient.post()
			.uri("/consumerMonoInput")
			.bodyValue("Consume Me (Mono Input)")
			.exchange()
			.expectStatus().isOk
	}

	/**
	 * 6. (Mono<T>) -> Mono<Void> -> consumerMono
	 * Consumer takes Mono<String>, returns Mono<Void>.
	 *
	 * --- Input: ---
	 * POST /consumerMono
	 * "Consume Me (Mono)"
	 *
	 * --- Output: ---
	 * 202 ACCEPTED
	 * (No body)
	 */
	@Test
	fun testConsumerMono() {
		webTestClient.post()
			.uri("/consumerMono")
			.bodyValue("Consume Me (Mono)")
			.exchange()
			.expectStatus().isOk
	}

	/**
	 * 7. (Flux<T>) -> Mono<Void> -> consumerFlux
	 * Consumer takes Flux<String>, returns Mono<Void>.
	 *
	 * --- Input: ---
	 * POST /consumerFlux
	 * Content-Type: application/json
	 * ["Consume","Flux","Items"]
	 *
	 * --- Output: ---
	 * 202 ACCEPTED
	 * (No body)
	 */
	@Test
	fun testConsumerFlux() {
		webTestClient.post()
			.uri("/consumerFlux")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("Consume", "Flux", "Items"))
			.exchange()
			.expectStatus().isOk
	}

	/**
	 * 8. (Message<T>) -> Unit -> consumerMessage
	 * Consumer takes Message<String> (side-effect only).
	 *
	 * --- Input: ---
	 * POST /consumerMessage
	 * Header: inputHeader=inValue
	 * "Consume Me (Message)"
	 *
	 * --- Output: ---
	 * 202 ACCEPTED
	 * (No body)
	 */
	@Test
	fun testConsumerMessage() {
		webTestClient.post()
			.uri("/consumerMessage")
			.header("inputHeader", "inValue")
			.bodyValue("Consume Me (Message)")
			.exchange()
			.expectStatus().isAccepted
	}

	/**
	 * 9. (Mono<Message<T>>) -> Mono<Void> -> consumerMonoMessage
	 * Consumer takes Mono<Message<String>>, returns Mono<Void>.
	 *
	 * --- Input: ---
	 * POST /consumerMonoMessage
	 * Header: monoMsgHeader=monoMsgValue
	 * "Consume Me (Mono Message)"
	 *
	 * --- Output: ---
	 * 202 ACCEPTED
	 * (No body)
	 */
	@Test
	fun testConsumerMonoMessage() {
		webTestClient.post()
			.uri("/consumerMonoMessage")
			.header("monoMsgHeader", "monoMsgValue")
			.bodyValue("Consume Me (Mono Message)")
			.exchange()
			.expectStatus().isOk
	}

	/**
	 * 10. suspend (Message<T>) -> Unit -> consumerSuspendMessage
	 * Suspending consumer takes Message<String>.
	 *
	 * --- Input: ---
	 * POST /consumerSuspendMessage
	 * Header: suspendInputHeader=susInValue
	 * "Consume Me (Suspend Message)"
	 *
	 * --- Output: ---
	 * 202 ACCEPTED
	 * (No body)
	 */
	@Test
	fun testConsumerSuspendMessage() {
		webTestClient.post()
			.uri("/consumerSuspendMessage")
			.header("suspendInputHeader", "susInValue")
			.bodyValue("Consume Me (Suspend Message)")
			.exchange()
			.expectStatus().isAccepted
	}

	/**
	 * 11. (Flux<Message<T>>) -> Unit -> consumerFluxMessage
	 * Consumer takes Flux<Message<String>>.
	 *
	 * --- Input: ---
	 * POST /consumerFluxMessage
	 * Content-Type: application/json
	 * Header: fluxInputHeader=fluxInValue
	 * ["Consume", "Flux", "Messages"]
	 *
	 * --- Output: ---
	 * 202 ACCEPTED
	 * (No body)
	 */
	@Test
	fun testConsumerFluxMessage() {
		webTestClient.post()
			.uri("/consumerFluxMessage")
			.contentType(MediaType.APPLICATION_JSON)
			.header("fluxInputHeader", "fluxInValue")
			.bodyValue(listOf("Consume", "Flux", "Messages"))
			.exchange()
			.expectStatus().isAccepted
	}

	/**
	 * 12. (Flow<Message<T>>) -> Unit -> consumerFlowMessage
	 * Consumer takes Flow<Message<String>>.
	 *
	 * --- Input: ---
	 * POST /consumerFlowMessage
	 * Content-Type: application/json
	 * Header: flowInputHeader=flowInValue
	 * ["Consume", "Flow", "Messages"]
	 *
	 * --- Output: ---
	 * 202 ACCEPTED
	 * (No body)
	 */
	@Test
	fun testConsumerFlowMessage() {
		webTestClient.post()
			.uri("/consumerFlowMessage")
			.contentType(MediaType.APPLICATION_JSON)
			.header("flowInputHeader", "flowInValue")
			.bodyValue(listOf("Consume", "Flow", "Messages"))
			.exchange()
			.expectStatus().isAccepted
	}

	/**
	 * 13. suspend (Flow<Message<T>>) -> Unit -> consumerSuspendFlowMessage
	 * Suspending consumer takes Flow<Message<String>>.
	 *
	 * --- Input: ---
	 * POST /consumerSuspendFlowMessage
	 * Content-Type: application/json
	 * Header: suspendFlowInputHeader=suspendFlowInValue
	 * ["Consume", "Suspend Flow", "Messages"]
	 *
	 * --- Output: ---
	 * 202 ACCEPTED
	 * (No body)
	 */
	@Test
	fun testConsumerSuspendFlowMessage() {
		webTestClient.post()
			.uri("/consumerSuspendFlowMessage")
			.contentType(MediaType.APPLICATION_JSON)
			.header("suspendFlowInputHeader", "suspendFlowInValue")
			.bodyValue(listOf("Consume", "Suspend Flow", "Messages"))
			.exchange()
			.expectStatus().isAccepted
	}
}
