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

import java.time.Duration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.function.context.test.FunctionalSpringBootTest
import org.springframework.cloud.function.kotlin.arity.KotlinArityApplication
import org.springframework.cloud.function.kotlin.arity.KotlinConsumerArityBean
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Test class for verifying the Kotlin Consumer examples in [KotlinConsumerArityBean].
 * Each bean is exposed at "/{beanName}" by Spring Cloud Function.
 *
 * ## Consumers Tested:
 * --- Coroutine ---
 * 1. (T) -> Unit                 -> consumerPlain
 * 2. (Flow<T>) -> Unit           -> consumerFlow
 * 3. suspend (T) -> Unit         -> consumerSuspendPlain
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
 *
 * @author Adrien Poupard
 */
@FunctionalSpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	classes = [KotlinArityApplication::class]
)
@AutoConfigureWebTestClient
@DirtiesContext
class KotlinConsumerArityBeanTest {

	@Autowired
	lateinit var webTestClient: WebTestClient

	@BeforeEach
	fun setup() {
		this.webTestClient = webTestClient.mutate()
			.responseTimeout(Duration.ofSeconds(120))
			.build()
	}

	/**
	 * 1. (T) -> Unit -> consumerPlain, consumerJavaPlain
	 * Takes a String (side-effect only).
	 *
	 * --- Input: ---
	 * POST /consumerPlain
	 * "Log me"
	 *
	 * --- Output: ---
	 * 202 ACCEPTED
	 * (No body)
	 */
	@ParameterizedTest
	@ValueSource(strings = ["consumerPlain", "consumerJavaPlain", "consumerKotlinPlain"])
	fun testConsumerPlain(name: String) {
		webTestClient.post()
			.uri("/$name")
			.bodyValue("Log me")
			.exchange()
			.expectStatus().isAccepted
	}

	/**
	 * 2. (Flow<T>) -> Unit -> consumerFlow, consumerJavaFlow
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
	@ParameterizedTest
	@ValueSource(strings = ["consumerFlow", "consumerJavaFlow", "consumerKotlinFlow"])
	fun testConsumerFlow(name: String) {
		webTestClient.post()
			.uri("/$name")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("one", "two"))
			.exchange()
			.expectStatus().isAccepted
	}

	/**
	 * 3. suspend (T) -> Unit -> consumerSuspendPlain
	 * Suspending consumer that takes a String (side-effect only).
	 *
	 * --- Input: ---
	 * POST /consumerSuspendPlain
	 * "test"
	 *
	 * --- Output: ---
	 * 202 ACCEPTED
	 * (No body)
	 */
	@ParameterizedTest
	@ValueSource(strings = ["consumerSuspendPlain", "consumerKotlinSuspendPlain"])
	fun testConsumerSuspendPlain(name: String) {
		webTestClient.post()
			.uri("/$name")
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
	@ParameterizedTest
	@ValueSource(strings = ["consumerSuspendFlow", "consumerKotlinSuspendFlow"])
	fun testConsumerSuspendFlow(name: String) {
		webTestClient.post()
			.uri("/$name")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("foo", "bar"))
			.exchange()
			.expectStatus().isAccepted
	}


	/**
	 * 5. (T) -> Mono<Void> -> consumerMonoInput, consumerJavaMonoInput
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
	@ParameterizedTest
	@ValueSource(strings = ["consumerMonoInput", "consumerJavaMonoInput", "consumerKotlinMonoInput"])
	fun testConsumerMonoInput(name: String) {
		webTestClient.post()
			.uri("/$name")
			.bodyValue("Consume Me (Mono Input)")
			.exchange()
			.expectStatus().isAccepted
	}

	/**
	 * 6. (Mono<T>) -> Mono<Void> -> consumerMono, consumerJavaMono
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
	@ParameterizedTest
	@ValueSource(strings = ["consumerMono", "consumerJavaMono", "consumerKotlinMono"])
	fun testConsumerMono(name: String) {
		webTestClient.post()
			.uri("/$name")
			.bodyValue("Consume Me (Mono)")
			.exchange()
			.expectStatus().isAccepted
	}

	/**
	 * 7. (Flux<T>) -> Mono<Void> -> consumerFlux, consumerJavaFlux
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
	@ParameterizedTest
	@ValueSource(strings = ["consumerFlux", "consumerJavaFlux", "consumerKotlinFlux"])
	fun testConsumerFlux(name: String) {
		webTestClient.post()
			.uri("/$name")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(listOf("Consume", "Flux", "Items"))
			.exchange()
			.expectStatus().isAccepted
	}

	/**
	 * 8. (Message<T>) -> Unit -> consumerMessage, consumerJavaMessage
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
	@ParameterizedTest
	@ValueSource(strings = ["consumerMessage", "consumerJavaMessage", "consumerKotlinMessage"])
	fun testConsumerMessage(name: String) {
		webTestClient.post()
			.uri("/$name")
			.header("inputHeader", "inValue")
			.bodyValue("Consume Me (Message)")
			.exchange()
			.expectStatus().isAccepted
	}

	/**
	 * 9. (Mono<Message<T>>) -> Mono<Void> -> consumerMonoMessage, consumerJavaMonoMessage
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
	@ParameterizedTest
	@ValueSource(strings = ["consumerMonoMessage", "consumerJavaMonoMessage", "consumerKotlinMonoMessage"])
	fun testConsumerMonoMessage(name: String) {
		webTestClient.post()
			.uri("/$name")
			.header("monoMsgHeader", "monoMsgValue")
			.bodyValue("Consume Me (Mono Message)")
			.exchange()
			.expectStatus().isAccepted
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
	@ParameterizedTest
	@ValueSource(strings = ["consumerSuspendMessage", "consumerKotlinSuspendMessage"])
	fun testConsumerSuspendMessage(name: String) {
		webTestClient.post()
			.uri("/$name")
			.header("suspendInputHeader", "susInValue")
			.bodyValue("Consume Me (Suspend Message)")
			.exchange()
			.expectStatus().isAccepted
	}

	/**
	 * 11. (Flux<Message<T>>) -> Unit -> consumerFluxMessage, consumerJavaFluxMessage
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
	@ParameterizedTest
	@ValueSource(strings = ["consumerFluxMessage", "consumerJavaFluxMessage", "consumerKotlinFluxMessage"])
	fun testConsumerFluxMessage(name: String) {
		webTestClient.post()
			.uri("/$name")
			.contentType(MediaType.APPLICATION_JSON)
			.header("fluxInputHeader", "fluxInValue")
			.bodyValue(listOf("Consume", "Flux", "Messages"))
			.exchange()
			.expectStatus().isAccepted
	}

	/**
	 * 12. (Flow<Message<T>>) -> Unit -> consumerFlowMessage, consumerJavaFlowMessage
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
	@ParameterizedTest
	@ValueSource(strings = ["consumerFlowMessage", "consumerJavaFlowMessage", "consumerKotlinFlowMessage"])
	fun testConsumerFlowMessage(name: String) {
		webTestClient.post()
			.uri("/$name")
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
	@ParameterizedTest
	@ValueSource(strings = ["consumerSuspendFlowMessage", "consumerKotlinSuspendFlowMessage"])
	fun testConsumerSuspendFlowMessage(name: String) {
		webTestClient.post()
			.uri("/$name")
			.contentType(MediaType.APPLICATION_JSON)
			.header("suspendFlowInputHeader", "suspendFlowInValue")
			.bodyValue(listOf("Consume", "Suspend Flow", "Messages"))
			.exchange()
			.expectStatus().isAccepted
	}
}
