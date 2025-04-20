package com.example.kotlin

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.cloud.function.context.test.FunctionalSpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.test.web.reactive.server.expectBodyList
import java.time.Duration
import java.util.UUID

/**
 * Test class for verifying the Kotlin Function examples in [KotlinFunctionExamples].
 * Each bean is exposed at "/{beanName}" by Spring Cloud Function.
 *
 * ## Functions Tested:
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
@FunctionalSpringBootTest
@AutoConfigureWebTestClient
class KotlinFunctionExamplesTest {

	@Autowired
	lateinit var webTestClient: WebTestClient

	@BeforeEach
	fun setup() {
		this.webTestClient = webTestClient.mutate()
			.responseTimeout(Duration.ofSeconds(120))
			.build()
	}

	/**
	 * 1. (T) -> R -> functionSingleToSingle
	 * Takes a String, returns its length (Int).
	 *
	 * --- Input: ---
	 * POST /functionSingleToSingle
	 * Content-type: application/json
	 * "Hello"
	 *
	 * --- Output: ---
	 * Status: 200 OK
	 * 5
	 */
	@Test
	fun testFunctionSingleToSingle() {
		webTestClient.post()
			.uri("/functionSingleToSingle")
			.bodyValue("Hello")
			.exchange()
			.expectStatus().isOk
			.expectBody<Int>()
			.isEqualTo(5)
	}

	/**
	 * 2. (T) -> Flow<R> -> functionSingleToFlow
	 * Takes a String, returns a Flow of its characters.
	 *
	 * --- Input: ---
	 * POST /functionSingleToFlow
	 * "test"
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["t","e","s","t"]
	 */
	@Test
	fun testFunctionSingleToFlow() {
		webTestClient.post()
			.uri("/functionSingleToFlow")
			.bodyValue("test")
			.exchange()
			.expectStatus().isOk
			.expectBody<String>()
			.isEqualTo("[\"t\",\"e\",\"s\",\"t\"]")
	}

	/**
	 * 3. (Flow<T>) -> R -> functionFlowToSingle
	 * Takes a Flow of Strings, returns an Int count of items.
	 *
	 * --- Input: ---
	 * POST /functionFlowToSingle
	 * Content-Type: application/json
	 * ["one","two","three"]
	 *
	 * --- Output: ---
	 * 200 OK
	 * [3]
	 */
	@Test
	fun testFunctionFlowToSingle() {
		webTestClient.post()
			.uri("/functionFlowToSingle")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("one", "two", "three"))
			.exchange()
			.expectStatus().isOk
			.expectBodyList<Int>()
			.hasSize(1)
			.contains(3)
	}

	/**
	 * 4. (Flow<T>) -> Flow<R> -> functionFlowToFlow
	 * Takes a Flow<Int>, returns a Flow<String>.
	 *
	 * --- Input: ---
	 * POST /functionFlowToFlow
	 * Content-Type: application/json
	 * [1, 2, 3]
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["1","2","3"]
	 */
	@Test
	fun testFunctionFlowToFlow() {
		webTestClient.post()
			.uri("/functionFlowToFlow")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf(1, 2, 3))
			.exchange()
			.expectStatus().isOk
			.expectBodyList(Int::class.java)
			.contains(1, 2, 3)
//			.isEqualTo("[\"1\",\"2\",\"3\"]")
	}

	/**
	 * 5. suspend (T) -> R -> functionSuspendSingleToSingle
	 * Suspending function that takes a String, returns Int (length).
	 *
	 * --- Input: ---
	 * POST /functionSuspendSingleToSingle
	 * "kotlin"
	 *
	 * --- Output: ---
	 * 200 OK
	 * [6]
	 */
	@Test
	fun testFunctionSuspendSingleToSingle() {
		webTestClient.post()
			.uri("/functionSuspendSingleToSingle")
			.bodyValue("kotlin")
			.exchange()
			.expectStatus().isOk
			.expectBodyList<Int>()
			.hasSize(1)
			.contains(6)
	}

	/**
	 * 6. suspend (T) -> Flow<R> -> functionSuspendSingleToFlow
	 * Takes a String, returns a Flow of its characters.
	 *
	 * --- Input: ---
	 * POST /functionSuspendSingleToFlow
	 * "demo"
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["d","e","m","o"]
	 */
	@Test
	fun testFunctionSuspendSingleToFlow() {
		webTestClient.post()
			.uri("/functionSuspendSingleToFlow")
			.bodyValue("demo")
			.exchange()
			.expectStatus().isOk
			.expectBody<List<String>>()
			.isEqualTo(listOf("d", "e", "m", "o"))
	}

	/**
	 * 7. suspend (Flow<T>) -> R -> functionSuspendFlowToSingle
	 * Suspending function that takes a Flow of Strings, returns an Int count.
	 *
	 * --- Input: ---
	 * POST /functionSuspendFlowToSingle
	 * Content-Type: application/json
	 * ["alpha","beta"]
	 *
	 * --- Output: ---
	 * 200 OK
	 * [2]
	 */
	@Test
	fun testFunctionSuspendFlowToSingle() {
		webTestClient.post()
			.uri("/functionSuspendFlowToSingle")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("alpha", "beta"))
			.exchange()
			.expectStatus().isOk
			.expectBodyList<Int>()
			.hasSize(1)
			.contains(2)
	}

	/**
	 * 8. suspend (Flow<T>) -> Flow<R> -> functionSuspendFlowToFlow
	 * Suspending function that takes a Flow<String>, returns a Flow<String> (uppercase).
	 *
	 * --- Input: ---
	 * POST /functionSuspendFlowToFlow
	 * Content-Type: application/json
	 * ["abc","xyz"]
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["ABC","XYZ"]
	 */
	@Test
	fun testFunctionSuspendFlowToFlow() {
		webTestClient.post()
			.uri("/functionSuspendFlowToFlow")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("abc", "xyz"))
			.exchange()
			.expectStatus().isOk
			.expectBody<String>()
			.isEqualTo("[\"ABC\",\"XYZ\"]")
	}

	/**
	 * 9. (T) -> Mono<R> -> functionSingleToMono
	 * Takes a String, returns a Mono<Int> (length).
	 *
	 * --- Input: ---
	 * POST /functionSingleToMono
	 * "Reactor"
	 *
	 * --- Output: ---
	 * 200 OK
	 * 7
	 */
	@Test
	fun testFunctionSingleToMono() {
		webTestClient.post()
			.uri("/functionSingleToMono")
			.bodyValue("Reactor")
			.exchange()
			.expectStatus().isOk
			.expectBody<Int>()
			.isEqualTo(7)
	}

	/**
	 * 10. (T) -> Flux<R> -> functionSingleToFlux
	 * Takes a String, returns a Flux<String> (characters).
	 *
	 * --- Input: ---
	 * POST /functionSingleToFlux
	 * "Flux"
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["F","l","u","x"]
	 */
	@Test
	fun testFunctionSingleToFlux() {
		webTestClient.post()
			.uri("/functionSingleToFlux")
			.bodyValue("Flux")
			.exchange()
			.expectStatus().isOk
			.expectBody<String>()
			.isEqualTo("[\"F\",\"l\",\"u\",\"x\"]")
	}

	/**
	 * 11. (Mono<T>) -> Mono<R> -> functionMonoToMono
	 * Takes a Mono<String>, returns a Mono<String> (uppercase).
	 *
	 * --- Input: ---
	 * POST /functionMonoToMono
	 * "input mono"
	 *
	 * --- Output: ---
	 * 200 OK
	 * "INPUT MONO"
	 */
	@Test
	fun testFunctionMonoToMono() {
		webTestClient.post()
			.uri("/functionMonoToMono")
			.bodyValue("input mono")
			.exchange()
			.expectStatus().isOk
			.expectBody<String>()
			.isEqualTo("INPUT MONO")
	}

	/**
	 * 12. (Flux<T>) -> Flux<R> -> functionFluxToFlux
	 * Takes a Flux<String>, returns a Flux<Int> (lengths).
	 *
	 * --- Input: ---
	 * POST /functionFluxToFlux
	 * Content-Type: application/json
	 * ["one","three","five"]
	 *
	 * --- Output: ---
	 * 200 OK
	 * [3,5,4]
	 */
	@Test
	fun testFunctionFluxToFlux() {
		webTestClient.post()
			.uri("/functionFluxToFlux")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("one", "three", "five"))
			.exchange()
			.expectStatus().isOk
			.expectBodyList<Int>()
			.contains(3, 5, 4)
	}

	/**
	 * 13. (Flux<T>) -> Mono<R> -> functionFluxToMono
	 * Takes a Flux<String>, returns a Mono<Int> (count).
	 *
	 * --- Input: ---
	 * POST /functionFluxToMono
	 * Content-Type: application/json
	 * ["a","b","c","d"]
	 *
	 * --- Output: ---
	 * 200 OK
	 * 4
	 */
	@Test
	fun testFunctionFluxToMono() {
		webTestClient.post()
			.uri("/functionFluxToMono")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("a", "b", "c", "d"))
			.exchange()
			.expectStatus().isOk
			.expectBodyList<Int>()
			.contains(4)
	}

	/**
	 * 14. (Message<T>) -> Message<R> -> functionMessageToMessage
	 * Takes Message<String>, returns Message<Int> (length), adds header.
	 *
	 * --- Input: ---
	 * POST /functionMessageToMessage
	 * Header: myHeader=myValue
	 * "message test"
	 *
	 * --- Output: ---
	 * 200 OK
	 * Header: processed=true
	 * Header: myHeader=myValue
	 * 12
	 */
	@Test
	fun testFunctionMessageToMessage() {
		webTestClient.post()
			.uri("/functionMessageToMessage")
			.contentType(MediaType.APPLICATION_JSON)
			.header("myHeader", "myValue")
			.bodyValue("message test")
			.exchange()
			.expectStatus().isOk
			.expectHeader().contentType(MediaType.APPLICATION_JSON)
			.expectHeader().valueEquals("processed", "true")
			.expectHeader().exists("myHeader")
			.expectBody<Int>()
			.isEqualTo(12)
	}

	/**
	 * 15. suspend (Message<T>) -> Message<R> -> functionSuspendMessageToMessage
	 * Suspending function takes Message<String>, returns Message<Int>.
	 *
	 * --- Input: ---
	 * POST /functionSuspendMessageToMessage
	 * Header: id=<uuid>
	 * Header: another=value
	 * "suspend msg"
	 *
	 * --- Output: ---
	 * 200 OK
	 * Header: suspend-processed=true
	 * Header: original-id=<uuid>
	 * Header: another=value
	 * 22
	 */
	@Test
	fun testFunctionSuspendMessageToMessage() {
		val inputId = UUID.randomUUID().toString()
		webTestClient.post()
			.uri("/functionSuspendMessageToMessage")
			.contentType(MediaType.APPLICATION_JSON)
			.header("id", inputId)
			.header("another", "value")
			.bodyValue("suspend msg")
			.exchange()
			.expectStatus().isOk
			.expectHeader().valueEquals("suspend-processed", "true")
//			.expectHeader().valueEquals("original-id", inputId)
			.expectHeader().exists("another")
			.expectBodyList<Int>()
			.contains(22)
	}

	/**
	 * 16. (Mono<Message<T>>) -> Mono<Message<R>> -> functionMonoMessageToMonoMessage
	 * Takes Mono<Message<String>>, returns Mono<Message<Int>> (hashcode).
	 *
	 * --- Input: ---
	 * POST /functionMonoMessageToMonoMessage
	 * Header: monoHeader=monoValue
	 * "test mono message"
	 *
	 * --- Output: ---
	 * 200 OK
	 * Header: mono-processed=true
	 * Header: monoHeader=monoValue
	 * <hashcode of "test mono message">
	 */
	@Test
	fun testFunctionMonoMessageToMonoMessage() {
		val inputPayload = "test mono message"
		val expectedPayload = inputPayload.hashCode()
		webTestClient.post()
			.uri("/functionMonoMessageToMonoMessage")
			.contentType(MediaType.APPLICATION_JSON)
			.header("monoHeader", "monoValue")
			.bodyValue(inputPayload)
			.exchange()
			.expectStatus().isOk
			.expectHeader().valueEquals("mono-processed", "true")
			.expectHeader().exists("monoHeader")
			.expectBody<Int>()
			.isEqualTo(expectedPayload)
	}

	/**
	 * 17. (Flux<Message<T>>) -> Flux<Message<R>> -> functionFluxMessageToFluxMessage
	 * Takes Flux<Message<String>>, returns Flux<Message<String>> (uppercase).
	 *
	 * --- Input: ---
	 * POST /functionFluxMessageToFluxMessage
	 * Content-Type: application/json
	 * ["msg one","msg two"]
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["MSG ONE", "MSG TWO"]
	 * (Headers flux-processed=true on each message)
	 */
	@Test
	fun testFunctionFluxMessageToFluxMessage() {
		webTestClient.post()
			.uri("/functionFluxMessageToFluxMessage")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("msg one", "msg two"))
			.exchange()
			.expectStatus().isOk
			.expectBody<List<String>>()
			.isEqualTo(listOf("MSG ONE", "MSG TWO"))
	}

	/**
	 * 18. (Flow<Message<T>>) -> Flow<Message<R>> -> functionFlowMessageToFlowMessage
	 * Takes Flow<Message<String>>, returns Flow<Message<String>> (reversed).
	 *
	 * --- Input: ---
	 * POST /functionFlowMessageToFlowMessage
	 * Content-Type: application/json
	 * ["flow one", "flow two"]
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["eno wolf", "owt wolf"]
	 * (Headers flow-processed=true on each message)
	 */
	@Test
	fun testFunctionFlowMessageToFlowMessage() {
		webTestClient.post()
			.uri("/functionFlowMessageToFlowMessage")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("flow one", "flow two"))
			.exchange()
			.expectStatus().isOk
			.expectBody<List<String>>()
			.isEqualTo(listOf("eno wolf", "owt wolf"))
	}

	/**
	 * 19. suspend (Flow<Message<T>>) -> Flow<Message<R>> -> functionSuspendFlowMessageToFlowMessage
	 * Suspending fn takes Flow<Message<String>>, returns Flow<Message<String>> (appended).
	 *
	 * --- Input: ---
	 * POST /functionSuspendFlowMessageToFlowMessage
	 * Content-Type: application/json
	 * ["sus flow one", "sus flow two"]
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["sus flow one SUSPEND", "sus flow two SUSPEND"]
	 * (Headers suspend-flow-processed=true on each message)
	 */
	@Test
	fun testFunctionSuspendFlowMessageToFlowMessage() {
		webTestClient.post()
			.uri("/functionSuspendFlowMessageToFlowMessage")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("sus flow one", "sus flow two"))
			.exchange()
			.expectStatus().isOk
			.expectBody<List<String>>()
			.isEqualTo(listOf("sus flow one SUSPEND", "sus flow two SUSPEND"))
	}
}
