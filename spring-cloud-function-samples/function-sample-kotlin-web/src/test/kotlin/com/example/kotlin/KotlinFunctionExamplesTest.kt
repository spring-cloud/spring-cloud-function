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
 * 1. (T) -> R                     -> functionPlainToPlain
 * 2. (T) -> Flow<R>               -> functionPlainToFlow
 * 3. (Flow<T>) -> R               -> functionFlowToPlain
 * 4. (Flow<T>) -> Flow<R>         -> functionFlowToFlow
 * 5. suspend (T) -> R             -> functionSuspendPlainToPlain
 * 6. suspend (T) -> Flow<R>       -> functionSuspendPlainToFlow
 * 7. suspend (Flow<T>) -> R       -> functionSuspendFlowToPlain
 * 8. suspend (Flow<T>) -> Flow<R> -> functionSuspendFlowToFlow
 * --- Reactor ---
 * 9. (T) -> Mono<R>               -> functionPlainToMono
 * 10. (T) -> Flux<R>              -> functionPlainToFlux
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
	 * 1. (T) -> R -> functionPlainToPlain
	 * Takes a String, returns its length (Int).
	 *
	 * --- Input: ---
	 * POST /functionPlainToPlain
	 * Content-type: application/json
	 * "Hello"
	 *
	 * --- Output: ---
	 * Status: 200 OK
	 * 5
	 */
	@Test
	fun testFunctionPlainToPlain() {
		webTestClient.post()
			.uri("/functionPlainToPlain")
			.bodyValue("Hello")
			.exchange()
			.expectStatus().isOk
			.expectBody<Int>()
			.isEqualTo(5)
	}

	/**
	 * 2. (T) -> Flow<R> -> functionPlainToFlow
	 * Takes a String, returns a Flow of its characters.
	 *
	 * --- Input: ---
	 * POST /functionPlainToFlow
	 * "test"
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["t","e","s","t"]
	 */
	@Test
	fun testFunctionPlainToFlow() {
		webTestClient.post()
			.uri("/functionPlainToFlow")
			.bodyValue("test")
			.exchange()
			.expectStatus().isOk
			.expectBody<String>()
			.isEqualTo("[\"t\",\"e\",\"s\",\"t\"]")
	}

	/**
	 * 3. (Flow<T>) -> R -> functionFlowToPlain
	 * Takes a Flow of Strings, returns an Int count of items.
	 *
	 * --- Input: ---
	 * POST /functionFlowToPlain
	 * Content-Type: application/json
	 * ["one","two","three"]
	 *
	 * --- Output: ---
	 * 200 OK
	 * [3]
	 */
	@Test
	fun testFunctionFlowToPlain() {
		webTestClient.post()
			.uri("/functionFlowToPlain")
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
	 * 5. suspend (T) -> R -> functionSuspendPlainToPlain
	 * Suspending function that takes a String, returns Int (length).
	 *
	 * --- Input: ---
	 * POST /functionSuspendPlainToPlain
	 * "kotlin"
	 *
	 * --- Output: ---
	 * 200 OK
	 * [6]
	 */
	@Test
	fun testFunctionSuspendPlainToPlain() {
		webTestClient.post()
			.uri("/functionSuspendPlainToPlain")
			.bodyValue("kotlin")
			.exchange()
			.expectStatus().isOk
			.expectBodyList<Int>()
			.hasSize(1)
			.contains(6)
	}

	/**
	 * 6. suspend (T) -> Flow<R> -> functionSuspendPlainToFlow
	 * Takes a String, returns a Flow of its characters.
	 *
	 * --- Input: ---
	 * POST /functionSuspendPlainToFlow
	 * "demo"
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["d","e","m","o"]
	 */
	@Test
	fun testFunctionSuspendPlainToFlow() {
		webTestClient.post()
			.uri("/functionSuspendPlainToFlow")
			.bodyValue("demo")
			.exchange()
			.expectStatus().isOk
			.expectBody<List<String>>()
			.isEqualTo(listOf("d", "e", "m", "o"))
	}

	/**
	 * 7. suspend (Flow<T>) -> R -> functionSuspendFlowToPlain
	 * Suspending function that takes a Flow of Strings, returns an Int count.
	 *
	 * --- Input: ---
	 * POST /functionSuspendFlowToPlain
	 * Content-Type: application/json
	 * ["alpha","beta"]
	 *
	 * --- Output: ---
	 * 200 OK
	 * [2]
	 */
	@Test
	fun testFunctionSuspendFlowToPlain() {
		webTestClient.post()
			.uri("/functionSuspendFlowToPlain")
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
	 * 9. (T) -> Mono<R> -> functionPlainToMono
	 * Takes a String, returns a Mono<Int> (length).
	 *
	 * --- Input: ---
	 * POST /functionPlainToMono
	 * "Reactor"
	 *
	 * --- Output: ---
	 * 200 OK
	 * 7
	 */
	@Test
	fun testFunctionPlainToMono() {
		webTestClient.post()
			.uri("/functionPlainToMono")
			.bodyValue("Reactor")
			.exchange()
			.expectStatus().isOk
			.expectBody<Int>()
			.isEqualTo(7)
	}

	/**
	 * 10. (T) -> Flux<R> -> functionPlainToFlux
	 * Takes a String, returns a Flux<String> (characters).
	 *
	 * --- Input: ---
	 * POST /functionPlainToFlux
	 * "Flux"
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["F","l","u","x"]
	 */
	@Test
	fun testFunctionPlainToFlux() {
		webTestClient.post()
			.uri("/functionPlainToFlux")
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
			.header("original-id", inputId)
			.header("another", "value")
			.bodyValue("suspend msg")
			.exchange()
			.expectStatus().isOk
			.expectHeader().valueEquals("suspend-processed", "true")
			.expectHeader().valueEquals("original-id", inputId)
			.expectHeader().exists("another")
			.expectBody<List<Int>>()
			.isEqualTo(listOf(22))
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
