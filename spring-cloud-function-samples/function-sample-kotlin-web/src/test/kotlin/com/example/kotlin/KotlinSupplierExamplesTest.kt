package com.example.kotlin

import java.time.Duration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.cloud.function.context.test.FunctionalSpringBootTest
import org.springframework.http.MediaType
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.test.web.reactive.server.expectBodyList

/**
 * Test class for verifying the Kotlin Supplier examples in [KotlinSupplierExamples].
 * Each bean is exposed at "/{beanName}" by Spring Cloud Function.
 *
 * ## Suppliers Tested:
 * --- Coroutine ---
 * 1. () -> R                      -> supplierSingle
 * 2. () -> Flow<R>               -> supplierFlow
 * 3. suspend () -> R             -> supplierSuspendSingle
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
 */
@FunctionalSpringBootTest
@AutoConfigureWebTestClient
class KotlinSupplierExamplesTest {

	@Autowired
	lateinit var webTestClient: WebTestClient

	@BeforeEach
	fun setup() {
		this.webTestClient = webTestClient.mutate()
			.responseTimeout(Duration.ofSeconds(120))
			.build()
	}

	/**
	 * 1. () -> R -> supplierSingle
	 * No input, returns an Int.
	 *
	 * --- Input: ---
	 * GET /supplierSingle
	 *
	 * --- Output: ---
	 * 200 OK
	 * 42
	 */
	@Test
	fun testSupplierSingle() {
		webTestClient.get()
			.uri("/supplierSingle")
			.exchange()
			.expectStatus().isOk
			.expectBody<Int>()
			.isEqualTo(42)
	}

	/**
	 * 2. () -> Flow<R> -> supplierFlow
	 * No input, returns a Flow of Strings.
	 *
	 * --- Input: ---
	 * GET /supplierFlow
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["A","B","C"]
	 */
	@Test
	fun testSupplierFlow() {
		webTestClient.get()
			.uri("/supplierFlow")
			.exchange()
			.expectStatus().isOk
			.expectBody<String>()
			.isEqualTo("[\"A\",\"B\",\"C\"]")
	}

	/**
	 * 3. suspend () -> R -> supplierSuspendSingle
	 * Suspending supplier that returns a single String.
	 *
	 * --- Input: ---
	 * GET /supplierSuspendSingle
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["Hello from suspend"]
	 */
	@Test
	fun testSupplierSuspendSingle() {
		webTestClient.get()
			.uri("/supplierSuspendSingle")
			.exchange()
			.expectStatus().isOk
			.expectBody<String>()
			.isEqualTo("[\"Hello from suspend\"]")
	}

	/**
	 * 4. suspend () -> Flow<R> -> supplierSuspendFlow
	 * Suspending supplier that returns a Flow of Strings.
	 *
	 * --- Input: ---
	 * GET /supplierSuspendFlow
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["x","y","z"]
	 */
	@Test
	fun testSupplierSuspendFlow() {
		webTestClient.get()
			.uri("/supplierSuspendFlow")
			.exchange()
			.expectStatus().isOk
			.expectBody<String>()
			.isEqualTo("[\"x\",\"y\",\"z\"]")
	}

	/**
	 * 5. () -> Mono<R> -> supplierMono
	 * Supplier that returns Mono<String>.
	 *
	 * --- Input: ---
	 * GET /supplierMono
	 *
	 * --- Output: ---
	 * 200 OK
	 * "Hello from Mono"
	 */
	@Test
	fun testSupplierMono() {
		webTestClient.get()
			.uri("/supplierMono")
			.exchange()
			.expectStatus().isOk
			.expectBody<String>()
			.isEqualTo("Hello from Mono")
	}

	/**
	 * 6. () -> Flux<R> -> supplierFlux
	 * Supplier that returns Flux<String>.
	 *
	 * --- Input: ---
	 * GET /supplierFlux
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["Alpha","Beta","Gamma"]
	 */
	@Test
	fun testSupplierFlux() {
		webTestClient.get()
			.uri("/supplierFlux")
			.exchange()
			.expectStatus().isOk
			.expectBody<String>()
			.isEqualTo("[\"Alpha\",\"Beta\",\"Gamma\"]")
	}

	/**
	 * 7. () -> Message<R> -> supplierMessage
	 * Supplier that returns Message<String> with a header.
	 *
	 * --- Input: ---
	 * GET /supplierMessage
	 *
	 * --- Output: ---
	 * 200 OK
	 * Header: messageId=<uuid>
	 * "Hello from Message"
	 */
	@Test
	fun testSupplierMessage() {
		webTestClient.get()
			.uri("/supplierMessage")
			.exchange()
			.expectStatus().isOk
			.expectHeader().exists("messageId")
			.expectBody<String>()
			.isEqualTo("Hello from Message")
	}

	/**
	 * 8. () -> Mono<Message<R>> -> supplierMonoMessage
	 * Supplier that returns Mono<Message<String>>.
	 *
	 * --- Input: ---
	 * GET /supplierMonoMessage
	 *
	 * --- Output: ---
	 * 200 OK
	 * Header: monoMessageId=<uuid>
	 * Header: source=mono
	 * "Hello from Mono Message"
	 */
	@Test
	fun testSupplierMonoMessage() {
		webTestClient.get()
			.uri("/supplierMonoMessage")
			.exchange()
			.expectStatus().isOk
			.expectHeader().exists("monoMessageId")
			.expectHeader().valueEquals("source", "mono")
			.expectBody<String>()
			.isEqualTo("Hello from Mono Message")
	}

	/**
	 * 9. suspend () -> Message<R> -> supplierSuspendMessage
	 * Suspending supplier that returns Message<String>.
	 *
	 * --- Input: ---
	 * GET /supplierSuspendMessage
	 *
	 * --- Output: ---
	 * 200 OK
	 * Header: suspendMessageId=<uuid>
	 * Header: wasSuspended=true
	 * "Hello from Suspend Message"
	 */
	@Test
	fun testSupplierSuspendMessage() {
		webTestClient.post()
			.uri("/supplierSuspendMessage")
			.accept(MediaType.APPLICATION_JSON)
			.exchange()
			.expectStatus().isOk
			.expectHeader().exists("suspendMessageId")
			.expectHeader().valueEquals("wasSuspended", "true")
			.expectBody<List<String>>()
			.isEqualTo(listOf("Hello from Suspend Message"))
	}

	/**
	 * 10. () -> Flux<Message<R>> -> supplierFluxMessage
	 * Supplier that returns Flux<Message<String>> with headers.
	 *
	 * --- Input: ---
	 * GET /supplierFluxMessage
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["Msg1", "Msg2"]
	 * (Headers fluxMessageId=<uuid> on each message)
	 */
	@Test
	fun testSupplierFluxMessage() {
		webTestClient.get()
			.uri("/supplierFluxMessage")
			.exchange()
			.expectStatus().isOk
			.expectBody<List<String>>()
			.isEqualTo(listOf("Msg1", "Msg2"))
	}

	/**
	 * 11. () -> Flow<Message<R>> -> supplierFlowMessage
	 * Supplier that returns Flow<Message<String>> with headers.
	 *
	 * --- Input: ---
	 * GET /supplierFlowMessage
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["FlowMsg1", "FlowMsg2"]
	 * (Headers flowMessageId=<uuid> on each message)
	 */
	@Test
	fun testSupplierFlowMessage() {
		webTestClient.get()
			.uri("/supplierFlowMessage")
			.accept(MediaType.APPLICATION_JSON)
			.exchange()
			.expectStatus().isOk
			.expectBody<List<SimpleMessage>>()
			.isEqualTo(
				listOf(
					SimpleMessage("FlowMsg1"),
					SimpleMessage("FlowMsg2")
				)
			)

	}

	/**
	 * 12. suspend () -> Flow<Message<R>> -> supplierSuspendFlowMessage
	 * Suspending supplier that returns Flow<Message<String>> with headers.
	 *
	 * --- Input: ---
	 * GET /supplierSuspendFlowMessage
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["SuspendFlowMsg1", "SuspendFlowMsg2"]
	 * (Headers suspendFlowMessageId=<uuid> on each message)
	 */
	@Test
	fun testSupplierSuspendFlowMessage() {
		webTestClient.get()
			.uri("/supplierSuspendFlowMessage")
			.exchange()
			.expectStatus().isOk
			.expectBody<List<String>>()
			.isEqualTo(listOf("SuspendFlowMsg1", "SuspendFlowMsg2"))
	}
}

data class SimpleMessage(
	val payload: String,
)
