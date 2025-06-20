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
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Test class for verifying the Kotlin Supplier examples in [KotlinSupplierExamples].
 * Each bean is exposed at "/{beanName}" by Spring Cloud Function.
 *
 * ## Suppliers Tested:
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
@FunctionalSpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	classes = [KotlinArityApplication::class]
)
@AutoConfigureWebTestClient
class KotlinSupplierArityBeanTest {

	@Autowired
	lateinit var webTestClient: WebTestClient

	@BeforeEach
	fun setup() {
		this.webTestClient = webTestClient.mutate()
			.responseTimeout(Duration.ofSeconds(120))
			.build()
	}

	/**
	 * 1. () -> R -> supplierPlain, supplierJavaPlain, supplierKotlinPlain
	 * No input, returns an Int.
	 *
	 * --- Input: ---
	 * GET /supplierPlain
	 *
	 * --- Output: ---
	 * 200 OK
	 * 42
	 */
	@ParameterizedTest
	@ValueSource(strings = ["supplierPlain", "supplierJavaPlain", "supplierKotlinPlain"])
	fun testSupplierPlain(name: String) {
		webTestClient.get()
			.uri("/$name")
			.exchange()
			.expectStatus().isOk
			.expectBody(Int::class.java)
			.isEqualTo(42)
	}

	/**
	 * 2. () -> Flow<R> -> supplierFlow, supplierJavaFlow, supplierKotlinFlow
	 * No input, returns a Flow of Strings.
	 *
	 * --- Input: ---
	 * GET /supplierFlow
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["A","B","C"]
	 */
	@ParameterizedTest
	@ValueSource(strings = ["supplierFlow", "supplierJavaFlow", "supplierKotlinFlow"])
	fun testSupplierFlow(name: String) {
		webTestClient.get()
			.uri("/$name")
			.exchange()
			.expectStatus().isOk
			.expectBody(String::class.java)
			.isEqualTo("[\"A\",\"B\",\"C\"]")
	}

	/**
	 * 3. suspend () -> R -> supplierSuspendPlain, supplierKotlinSuspendPlain
	 * Suspending supplier that returns a single String.
	 *
	 * --- Input: ---
	 * GET /supplierSuspendPlain
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["Hello from suspend"]
	 */
	@ParameterizedTest
	@ValueSource(strings = ["supplierSuspendPlain", "supplierKotlinSuspendPlain"])
	fun testSupplierSuspendPlain(name: String) {
		webTestClient.get()
			.uri("/$name")
			.exchange()
			.expectStatus().isOk
			.expectBody(String::class.java)
			.isEqualTo("[\"Hello from suspend\"]")
	}

	/**
	 * 4. suspend () -> Flow<R> -> supplierSuspendFlow, supplierKotlinSuspendFlow
	 * Suspending supplier that returns a Flow of Strings.
	 *
	 * --- Input: ---
	 * GET /supplierSuspendFlow
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["x","y","z"]
	 */
	@ParameterizedTest
	@ValueSource(strings = ["supplierSuspendFlow", "supplierKotlinSuspendFlow"])
	fun testSupplierSuspendFlow(name: String) {
		webTestClient.get()
			.uri("/$name")
			.exchange()
			.expectStatus().isOk
			.expectBody(String::class.java)
			.isEqualTo("[\"x\",\"y\",\"z\"]")
	}

	/**
	 * 5. () -> Mono<R> -> supplierMono, supplierJavaMono, supplierKotlinMono
	 * Supplier that returns Mono<String>.
	 *
	 * --- Input: ---
	 * GET /supplierMono
	 *
	 * --- Output: ---
	 * 200 OK
	 * "Hello from Mono"
	 */
	@ParameterizedTest
	@ValueSource(strings = ["supplierMono", "supplierJavaMono", "supplierKotlinMono"])
	fun testSupplierMono(name: String) {
		webTestClient.get()
			.uri("/$name")
			.exchange()
			.expectStatus().isOk
			.expectBody(String::class.java)
			.isEqualTo("Hello from Mono")
	}

	/**
	 * 6. () -> Flux<R> -> supplierFlux, supplierJavaFlux, supplierKotlinFlux
	 * Supplier that returns Flux<String>.
	 *
	 * --- Input: ---
	 * GET /supplierFlux
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["Alpha","Beta","Gamma"]
	 */
	@ParameterizedTest
	@ValueSource(strings = ["supplierFlux", "supplierJavaFlux", "supplierKotlinFlux"])
	fun testSupplierFlux(name: String) {
		webTestClient.get()
			.uri("/$name")
			.exchange()
			.expectStatus().isOk
			.expectBody(String::class.java)
			.isEqualTo("[\"Alpha\",\"Beta\",\"Gamma\"]")
	}

	/**
	 * 7. () -> Message<R> -> supplierMessage, supplierJavaMessage, supplierKotlinMessage
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
	@ParameterizedTest
	@ValueSource(strings = ["supplierMessage", "supplierJavaMessage", "supplierKotlinMessage"])
	fun testSupplierMessage(name: String) {
		webTestClient.get()
			.uri("/$name")
			.exchange()
			.expectStatus().isOk
			.expectHeader().exists("messageId")
			.expectBody(String::class.java)
			.isEqualTo("Hello from Message")
	}

	/**
	 * 8. () -> Mono<Message<R>> -> supplierMonoMessage, supplierJavaMonoMessage, supplierKotlinMonoMessage
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
	@ParameterizedTest
	@ValueSource(strings = ["supplierMonoMessage", "supplierJavaMonoMessage", "supplierKotlinMonoMessage"])
	fun testSupplierMonoMessage(name: String) {
		webTestClient.get()
			.uri("/$name")
			.exchange()
			.expectStatus().isOk
			.expectHeader().exists("monoMessageId")
			.expectHeader().valueEquals("source", "mono")
			.expectBody(String::class.java)
			.isEqualTo("Hello from Mono Message")
	}

	/**
	 * 9. suspend () -> Message<R> -> supplierSuspendMessage, supplierKotlinSuspendMessage
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
	@ParameterizedTest
	@ValueSource(strings = ["supplierSuspendMessage", "supplierKotlinSuspendMessage"])
	fun testSupplierSuspendMessage(name: String) {
		webTestClient.get()
			.uri("/$name")
			.accept(MediaType.APPLICATION_JSON)
			.exchange()
			.expectStatus().isOk
			.expectHeader().exists("suspendMessageId")
			.expectHeader().valueEquals("wasSuspended", "true")
			.expectBody(ParameterizedTypeReference.forType<List<String>>(List::class.java))
			.isEqualTo(listOf("Hello from Suspend Message"))
	}

	/**
	 * 10. () -> Flux<Message<R>> -> supplierFluxMessage, supplierJavaFluxMessage, supplierKotlinFluxMessage
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
	@ParameterizedTest
	@ValueSource(strings = ["supplierFluxMessage", "supplierJavaFluxMessage", "supplierKotlinFluxMessage"])
	fun testSupplierFluxMessage(name: String) {
		webTestClient.get()
			.uri("/$name")
			.exchange()
			.expectStatus().isOk
			.expectBody(ParameterizedTypeReference.forType<List<String>>(List::class.java))
			.isEqualTo(listOf("Msg1", "Msg2"))
	}

	/**
	 * 11. () -> Flow<Message<R>> -> supplierFlowMessage, supplierJavaFlowMessage, supplierKotlinFlowMessage
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
	@ParameterizedTest
	@ValueSource(strings = ["supplierFlowMessage", "supplierJavaFlowMessage", "supplierKotlinFlowMessage"])
	fun testSupplierFlowMessage(name: String) {
		webTestClient.get()
			.uri("/$name")
			.accept(MediaType.APPLICATION_JSON)
			.exchange()
			.expectStatus().isOk
			.expectBody(ParameterizedTypeReference.forType<List<String>>(List::class.java))
			.isEqualTo(
				listOf(
					"FlowMsg1",
					"FlowMsg2"
				)
			)
	}

	/**
	 * 12. suspend () -> Flow<Message<R>> -> supplierSuspendFlowMessage, supplierKotlinSuspendFlowMessage
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
	@ParameterizedTest
	@ValueSource(strings = ["supplierSuspendFlowMessage", "supplierKotlinSuspendFlowMessage"])
	fun testSupplierSuspendFlowMessage(name: String) {
		webTestClient.get()
			.uri("/$name")
			.exchange()
			.expectStatus().isOk
			.expectBody(ParameterizedTypeReference.forType<List<String>>(List::class.java))
			.isEqualTo(listOf("SuspendFlowMsg1", "SuspendFlowMsg2"))
	}
}
