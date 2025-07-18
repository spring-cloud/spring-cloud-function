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

package org.springframework.cloud.function.kotlin.web

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.function.context.test.FunctionalSpringBootTest
import org.springframework.cloud.function.kotlin.arity.KotlinArityApplication
import org.springframework.cloud.function.kotlin.arity.KotlinFunctionArityBean
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Duration
import java.util.UUID

/**
 * Test class for verifying the Kotlin Function examples in [KotlinFunctionArityBean], [KotlinFunctionJavaExamples], and [KotlinFunctionKotlinExamples].
 * Each bean is exposed at "/{beanName}" by Spring Cloud Function.
 *
 * ## Functions Tested:
 * --- Coroutine ---
 * 1. (T) -> R                     -> functionPlainToPlain, functionJavaPlainToPlain, functionKotlinPlainToPlain
 * 2. (T) -> Flow<R>               -> functionPlainToFlow, functionJavaPlainToFlow, functionKotlinPlainToFlow
 * 3. (Flow<T>) -> R               -> functionFlowToPlain, functionJavaFlowToPlain, functionKotlinFlowToPlain
 * 4. (Flow<T>) -> Flow<R>         -> functionFlowToFlow, functionJavaFlowToFlow, functionKotlinFlowToFlow
 * 5. suspend (T) -> R             -> functionSuspendPlainToPlain, functionJavaSuspendPlainToPlain, functionKotlinSuspendPlainToPlain
 * 6. suspend (T) -> Flow<R>       -> functionSuspendPlainToFlow, functionJavaSuspendPlainToFlow, functionKotlinSuspendPlainToFlow
 * 7. suspend (Flow<T>) -> R       -> functionSuspendFlowToPlain, functionJavaSuspendFlowToPlain, functionKotlinSuspendFlowToPlain
 * 8. suspend (Flow<T>) -> Flow<R> -> functionSuspendFlowToFlow, functionJavaSuspendFlowToFlow, functionKotlinSuspendFlowToFlow
 * --- Reactor ---
 * 9. (T) -> Mono<R>               -> functionPlainToMono, functionJavaPlainToMono, functionKotlinPlainToMono
 * 10. (T) -> Flux<R>              -> functionPlainToFlux, functionJavaPlainToFlux, functionKotlinPlainToFlux
 * 11. (Mono<T>) -> Mono<R>        -> functionMonoToMono, functionJavaMonoToMono, functionKotlinMonoToMono
 * 12. (Flux<T>) -> Flux<R>        -> functionFluxToFlux, functionJavaFluxToFlux, functionKotlinFluxToFlux
 * 13. (Flux<T>) -> Mono<R>        -> functionFluxToMono, functionJavaFluxToMono, functionKotlinFluxToMono
 * --- Message<T> ---
 * 14. (Message<T>) -> Message<R>  -> functionMessageToMessage, functionJavaMessageToMessage, functionKotlinMessageToMessage
 * 15. suspend (Message<T>) -> Message<R> -> functionSuspendMessageToMessage, functionJavaSuspendMessageToMessage, functionKotlinSuspendMessageToMessage
 * 16. (Mono<Message<T>>) -> Mono<Message<R>> -> functionMonoMessageToMonoMessage, functionJavaMonoMessageToMonoMessage, functionKotlinMonoMessageToMonoMessage
 * 17. (Flux<Message<T>>) -> Flux<Message<R>> -> functionFluxMessageToFluxMessage, functionJavaFluxMessageToFluxMessage, functionKotlinFluxMessageToFluxMessage
 * 18. (Flow<Message<T>>) -> Flow<Message<R>> -> functionFlowMessageToFlowMessage, functionJavaFlowMessageToFlowMessage, functionKotlinFlowMessageToFlowMessage
 * 19. suspend (Flow<Message<T>>) -> Flow<Message<R>> -> functionSuspendFlowMessageToFlowMessage, functionJavaSuspendFlowMessageToFlowMessage, functionKotlinSuspendFlowMessageToFlowMessage
 *
 * @author Adrien Poupard
 */
@FunctionalSpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	classes = [KotlinArityApplication::class]
)
@AutoConfigureWebTestClient
@DirtiesContext
class KotlinFunctionArityBeanTest {

	@Autowired
	lateinit var webTestClient: WebTestClient

	@BeforeEach
	fun setup() {
		this.webTestClient = webTestClient.mutate()
			.responseTimeout(Duration.ofSeconds(120))
			.build()
	}

	/**
	 * 1. (T) -> R -> functionPlainToPlain, functionJavaPlainToPlain, functionKotlinPlainToPlain
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
	@ParameterizedTest
	@ValueSource(strings = ["functionPlainToPlain", "functionJavaPlainToPlain", "functionKotlinPlainToPlain"])
	fun testFunctionPlainToPlain(name: String) {
		webTestClient.post()
			.uri("/$name")
			.bodyValue("Hello")
			.exchange()
			.expectStatus().isOk
			.expectBody(Int::class.java)
			.isEqualTo(5)
	}

	/**
	 * 2. (T) -> Flow<R> -> functionPlainToFlow, functionJavaPlainToFlow, functionKotlinPlainToFlow
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
	@ParameterizedTest
	@ValueSource(strings = ["functionPlainToFlow", "functionJavaPlainToFlow", "functionKotlinPlainToFlow"])
	fun testFunctionPlainToFlow(name: String) {
		webTestClient.post()
			.uri("/$name")
			.bodyValue("test")
			.exchange()
			.expectStatus().isOk
			.expectBody(String::class.java)
			.isEqualTo("[\"t\",\"e\",\"s\",\"t\"]")
	}

	/**
	 * 3. (Flow<T>) -> R -> functionFlowToPlain, functionJavaFlowToPlain, functionKotlinFlowToPlain
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
	@ParameterizedTest
	@ValueSource(strings = ["functionFlowToPlain", "functionJavaFlowToPlain", "functionKotlinFlowToPlain"])
	fun testFunctionFlowToPlain(name: String) {
		webTestClient.post()
			.uri("/$name")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("one", "two", "three"))
			.exchange()
			.expectStatus().isOk
			.expectBodyList(Int::class.java)
			.hasSize(1)
			.contains(3)
	}

	/**
	 * 4. (Flow<T>) -> Flow<R> -> functionFlowToFlow, functionJavaFlowToFlow, functionKotlinFlowToFlow
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
	@ParameterizedTest
	@ValueSource(strings = ["functionFlowToFlow", "functionJavaFlowToFlow", "functionKotlinFlowToFlow"])
	fun testFunctionFlowToFlow(name: String) {
		webTestClient.post()
			.uri("/$name")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf(1, 2, 3))
			.exchange()
			.expectStatus().isOk
			.expectBodyList(Int::class.java)
			.contains(1, 2, 3)
//			.isEqualTo("[\"1\",\"2\",\"3\"]")
	}

	/**
	 * 5. suspend (T) -> R -> functionSuspendPlainToPlain, functionKotlinSuspendPlainToPlain
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
	@ParameterizedTest
	@ValueSource(strings = ["functionSuspendPlainToPlain", "functionKotlinSuspendPlainToPlain"])
	fun testFunctionSuspendPlainToPlain(name: String) {
		webTestClient.post()
			.uri("/$name")
			.bodyValue("kotlin")
			.exchange()
			.expectStatus().isOk
			.expectBodyList(Int::class.java)
			.hasSize(1)
			.contains(6)
	}

	/**
	 * 6. suspend (T) -> Flow<R> -> functionSuspendPlainToFlow, functionKotlinSuspendPlainToFlow
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
	@ParameterizedTest
	@ValueSource(strings = ["functionSuspendPlainToFlow", "functionKotlinSuspendPlainToFlow"])
	fun testFunctionSuspendPlainToFlow(name: String) {
		webTestClient.post()
			.uri("/$name")
			.bodyValue("demo")
			.exchange()
			.expectStatus().isOk
			.expectBody(ParameterizedTypeReference.forType<List<String>>(List::class.java))
			.isEqualTo(listOf("d", "e", "m", "o"))
	}

	/**
	 * 7. suspend (Flow<T>) -> R -> functionSuspendFlowToPlain, functionKotlinSuspendFlowToPlain
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
	@ParameterizedTest
	@ValueSource(strings = ["functionSuspendFlowToPlain", "functionKotlinSuspendFlowToPlain"])
	fun testFunctionSuspendFlowToPlain(name: String) {
		webTestClient.post()
			.uri("/$name")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("alpha", "beta"))
			.exchange()
			.expectStatus().isOk
			.expectBodyList(Int::class.java)
			.hasSize(1)
			.contains(2)
	}

	/**
	 * 8. suspend (Flow<T>) -> Flow<R> -> functionSuspendFlowToFlow, functionKotlinSuspendFlowToFlow
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
	@ParameterizedTest
	@ValueSource(strings = ["functionSuspendFlowToFlow", "functionKotlinSuspendFlowToFlow"])
	fun testFunctionSuspendFlowToFlow(name: String) {
		webTestClient.post()
			.uri("/$name")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("abc", "xyz"))
			.exchange()
			.expectStatus().isOk
			.expectBody(String::class.java)
			.isEqualTo("[\"ABC\",\"XYZ\"]")
	}

	/**
	 * 9. (T) -> Mono<R> -> functionPlainToMono, functionJavaPlainToMono, functionKotlinPlainToMono
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
	@ParameterizedTest
	@ValueSource(strings = ["functionPlainToMono", "functionJavaPlainToMono", "functionKotlinPlainToMono"])
	fun testFunctionPlainToMono(name: String) {
		webTestClient.post()
			.uri("/$name")
			.bodyValue("Reactor")
			.exchange()
			.expectStatus().isOk
			.expectBody(Int::class.java)
			.isEqualTo(7)
	}

	/**
	 * 10. (T) -> Flux<R> -> functionPlainToFlux, functionJavaPlainToFlux, functionKotlinPlainToFlux
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
	@ParameterizedTest
	@ValueSource(strings = ["functionPlainToFlux", "functionJavaPlainToFlux", "functionKotlinPlainToFlux"])
	fun testFunctionPlainToFlux(name: String) {
		webTestClient.post()
			.uri("/$name")
			.bodyValue("Flux")
			.exchange()
			.expectStatus().isOk
			.expectBody(String::class.java)
			.isEqualTo("[\"F\",\"l\",\"u\",\"x\"]")
	}

	/**
	 * 11. (Mono<T>) -> Mono<R> -> functionMonoToMono, functionJavaMonoToMono, functionKotlinMonoToMono
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
	@ParameterizedTest
	@ValueSource(strings = ["functionMonoToMono", "functionJavaMonoToMono", "functionKotlinMonoToMono"])
	fun testFunctionMonoToMono(name: String) {
		webTestClient.post()
			.uri("/$name")
			.bodyValue("input mono")
			.exchange()
			.expectStatus().isOk
			.expectBody(String::class.java)
			.isEqualTo("INPUT MONO")
	}

	/**
	 * 12. (Flux<T>) -> Flux<R> -> functionFluxToFlux, functionJavaFluxToFlux, functionKotlinFluxToFlux
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
	@ParameterizedTest
	@ValueSource(strings = ["functionFluxToFlux", "functionJavaFluxToFlux", "functionKotlinFluxToFlux"])
	fun testFunctionFluxToFlux(name: String) {
		webTestClient.post()
			.uri("/$name")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("one", "three", "five"))
			.exchange()
			.expectStatus().isOk
			.expectBodyList(Int::class.java)
			.contains(3, 5, 4)
	}

	/**
	 * 13. (Flux<T>) -> Mono<R> -> functionFluxToMono, functionJavaFluxToMono, functionKotlinFluxToMono
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
	@ParameterizedTest
	@ValueSource(strings = ["functionFluxToMono", "functionJavaFluxToMono", "functionKotlinFluxToMono"])
	fun testFunctionFluxToMono(name: String) {
		webTestClient.post()
			.uri("/$name")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("a", "b", "c", "d"))
			.exchange()
			.expectStatus().isOk
			.expectBodyList(Int::class.java)
			.contains(4)
	}

	/**
	 * 14. (Message<T>) -> Message<R> -> functionMessageToMessage, functionJavaMessageToMessage, functionKotlinMessageToMessage
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
	@ParameterizedTest
	@ValueSource(strings = ["functionMessageToMessage", "functionJavaMessageToMessage", "functionKotlinMessageToMessage"])
	fun testFunctionMessageToMessage(name: String) {
		webTestClient.post()
			.uri("/$name")
			.contentType(MediaType.APPLICATION_JSON)
			.header("myHeader", "myValue")
			.bodyValue("\"message test\"")
			.exchange()
			.expectStatus().isOk
			.expectHeader().contentType(MediaType.APPLICATION_JSON)
			.expectHeader().valueEquals("processed", "true")
			.expectHeader().exists("myHeader")
			.expectBody(Int::class.java)
			.isEqualTo(14)
	}

	/**
	 * 15. suspend (Message<T>) -> Message<R> -> functionSuspendMessageToMessage, functionKotlinSuspendMessageToMessage
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
	@ParameterizedTest
	@ValueSource(strings = ["functionSuspendMessageToMessage", "functionKotlinSuspendMessageToMessage"])
	fun testFunctionSuspendMessageToMessage(name: String) {
		val inputId = UUID.randomUUID().toString()
		webTestClient.post()
			.uri("/$name")
			.contentType(MediaType.APPLICATION_JSON)
			.header("original-id", inputId)
			.header("another", "value")
			.bodyValue("\"suspend msg\"")
			.exchange()
			.expectStatus().isOk
			.expectHeader().valueEquals("suspend-processed", "true")
			.expectHeader().valueEquals("original-id", inputId)
			.expectHeader().exists("another")
			.expectBodyList(Int::class.java)
			.contains(26)
	}

	/**
	 * 16. (Mono<Message<T>>) -> Mono<Message<R>> -> functionMonoMessageToMonoMessage, functionJavaMonoMessageToMonoMessage, functionKotlinMonoMessageToMonoMessage
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
	@ParameterizedTest
	@ValueSource(strings = ["functionMonoMessageToMonoMessage", "functionJavaMonoMessageToMonoMessage", "functionKotlinMonoMessageToMonoMessage"])
	fun testFunctionMonoMessageToMonoMessage(name: String) {
		val inputPayload = "test mono message"
		val expectedPayload = "test mono message".hashCode()
		webTestClient.post()
			.uri("/$name")
			.contentType(MediaType.APPLICATION_JSON)
			.header("monoHeader", "monoValue")
			.bodyValue(inputPayload)
			.exchange()
			.expectStatus().isOk
			.expectHeader().valueEquals("mono-processed", "true")
			.expectHeader().exists("monoHeader")
			.expectBody(Int::class.java)
			.isEqualTo(expectedPayload)
	}

	/**
	 * 17. (Flux<Message<T>>) -> Flux<Message<R>> -> functionFluxMessageToFluxMessage, functionJavaFluxMessageToFluxMessage, functionKotlinFluxMessageToFluxMessage
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
	@ParameterizedTest
	@ValueSource(strings = ["functionFluxMessageToFluxMessage", "functionJavaFluxMessageToFluxMessage", "functionKotlinFluxMessageToFluxMessage"])
	fun testFunctionFluxMessageToFluxMessage(name: String) {
		webTestClient.post()
			.uri("/$name")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("msg one", "msg two"))
			.exchange()
			.expectStatus().isOk
			.expectBody(ParameterizedTypeReference.forType<List<String>>(List::class.java))
			.isEqualTo(listOf("MSG ONE", "MSG TWO"))
	}

	/**
	 * 18. (Flow<Message<T>>) -> Flow<Message<R>> -> functionFlowMessageToFlowMessage, functionJavaFlowMessageToFlowMessage, functionKotlinFlowMessageToFlowMessage
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
	@ParameterizedTest
	@ValueSource(strings = ["functionFlowMessageToFlowMessage", "functionJavaFlowMessageToFlowMessage", "functionKotlinFlowMessageToFlowMessage"])
	fun testFunctionFlowMessageToFlowMessage(name: String) {
		webTestClient.post()
			.uri("/$name")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("flow one", "flow two"))
			.exchange()
			.expectStatus().isOk
			.expectBody(ParameterizedTypeReference.forType<List<String>>(List::class.java))
			.isEqualTo(listOf("eno wolf", "owt wolf"))
	}

	/**
	 * 19. suspend (Flow<Message<T>>) -> Flow<Message<R>> -> functionSuspendFlowMessageToFlowMessage, functionKotlinSuspendFlowMessageToFlowMessage
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
	@ParameterizedTest
	@ValueSource(strings = ["functionSuspendFlowMessageToFlowMessage", "functionKotlinSuspendFlowMessageToFlowMessage"])
	fun testFunctionSuspendFlowMessageToFlowMessage(name: String) {
		webTestClient.post()
			.uri("/$name")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("sus flow one", "sus flow two"))
			.exchange()
			.expectStatus().isOk
			.expectBody(ParameterizedTypeReference.forType<List<String>>(List::class.java))
			.isEqualTo(listOf("sus flow one SUSPEND", "sus flow two SUSPEND"))
	}
}
