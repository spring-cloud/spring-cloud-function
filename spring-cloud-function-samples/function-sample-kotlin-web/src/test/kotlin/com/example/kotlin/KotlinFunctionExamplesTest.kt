package com.example.kotlin

import java.time.Duration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.cloud.function.context.test.FunctionalSpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Test class for verifying the 16 Kotlin function arities in [KotlinFunctionExamples].
 * Each bean is exposed at "/{beanName}" by Spring Cloud Function (illustration only).
 *
 * Flow-based or suspend-based endpoints normally require adapters.
 * This class simulates how you might test them if such adapters existed.
 */
@FunctionalSpringBootTest
@AutoConfigureWebTestClient
class KotlinFunctionExamplesTest() {

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
			.expectBody(Int::class.java)
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
			.expectBody(String::class.java)
			.isEqualTo("[\"t\",\"e\",\"s\",\"t\"]")
	}

	/**
	 * 3. (Flow<T>) -> R -> functionFlowToSingle
	 * Takes a Flow of Strings, returns an Int count of items.
	 *
	 * --- Input: ---
	 * POST /functionFlowToSingle
	 * ["one","two","three"]
	 *
	 * --- Output: ---
	 * 200 OK
	 * 3
	 */
	@Test
	fun testFunctionFlowToSingle() {
		webTestClient.post()
			.uri("/functionFlowToSingle")
			.bodyValue(listOf("one", "two", "three"))
			.exchange()
			.expectStatus().isOk
			.expectBodyList(Int::class.java)
			.hasSize(1)
			.contains(3)
	}

	/**
	 * 4. (Flow<T>) -> Flow<R> -> functionFlowToFlow
	 * Takes a Flow<Int>, returns a Flow<String>.
	 *
	 * --- Input: ---
	 * POST /functionFlowToFlow
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
			.bodyValue(listOf(1, 2, 3))
			.exchange()
			.expectStatus().isOk
			.expectBody(String::class.java)
			.isEqualTo("[\"1\",\"2\",\"3\"]")
	}

	/**
	 * 5. suspend (T) -> R -> suspendFunctionSingleToSingle
	 * Suspending function that takes a String, returns Int (length).
	 *
	 * --- Input: ---
	 * POST /suspendFunctionSingleToSingle
	 * "kotlin"
	 *
	 * --- Output: ---
	 * 200 OK
	 * 6
	 */
	@Test
	fun testSuspendFunctionSingleToSingle() {
		webTestClient.post()
			.uri("/suspendFunctionSingleToSingle")
			.bodyValue("kotlin")
			.exchange()
			.expectStatus().isOk
			.expectBodyList(Int::class.java)
			.hasSize(1)
			.contains(6)
	}

	/**
	 * 6. suspend (T) -> Flow<R> -> suspendFunctionSingleToFlow
	 * Takes a String, returns a Flow of its characters.
	 *
	 * --- Input: ---
	 * POST /suspendFunctionSingleToFlow
	 * "demo"
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["d","e","m","o"]
	 */
	@Test
	fun testSuspendFunctionSingleToFlow() {
		webTestClient.post()
			.uri("/suspendFunctionSingleToFlow")
			.bodyValue("demo")
			.exchange()
			.expectStatus().isOk
			.expectBody(String::class.java)
			.isEqualTo("[\"d\",\"e\",\"m\",\"o\"]")
	}

	/**
	 * 7. suspend (Flow<T>) -> R -> suspendFunctionFlowToSingle
	 * Suspending function that takes a Flow of Strings, returns an Int count.
	 *
	 * --- Input: ---
	 * POST /suspendFunctionFlowToSingle
	 * ["alpha","beta"]
	 *
	 * --- Output: ---
	 * 200 OK
	 * 2
	 */
	@Test
	fun testSuspendFunctionFlowToSingle() {
		webTestClient.post()
			.uri("/suspendFunctionFlowToSingle")
			.bodyValue(listOf("alpha", "beta"))
			.exchange()
			.expectStatus().isOk
			.expectBodyList(Int::class.java)
			.hasSize(1)
			.contains(2)
	}

	/**
	 * 8. suspend (Flow<T>) -> Flow<R> -> suspendFunctionFlowToFlow
	 * Suspending function that takes a Flow<String>, returns a Flow<String> (uppercase).
	 *
	 * --- Input: ---
	 * POST /suspendFunctionFlowToFlow
	 * ["abc","xyz"]
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["ABC","XYZ"]
	 */
	@Test
	fun suspendFunctionFlowToFlow() {
		webTestClient.post()
			.uri("/suspendFunctionFlowToFlow")
			.bodyValue(listOf("abc", "xyz"))
			.exchange()
			.expectStatus().isOk
			.expectBody(String::class.java)
			.isEqualTo("[\"ABC\",\"XYZ\"]")
	}

	/**
	 * 9. () -> R -> supplierSingle
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
			.expectBody(Int::class.java)
			.isEqualTo(42)
	}

	/**
	 * 10. () -> Flow<R> -> supplierFlow
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
			.expectBody(String::class.java)
			.isEqualTo("[\"A\",\"B\",\"C\"]")
	}

	/**
	 * 11. suspend () -> R -> suspendSupplier
	 * Suspending supplier that returns a single String.
	 *
	 * --- Input: ---
	 * GET /suspendSupplier
	 *
	 * --- Output: ---
	 * 200 OK
	 * "Hello from suspend"
	 */
	@Test
	fun testSuspendSupplier() {
		webTestClient.get()
			.uri("/suspendSupplier")
			.exchange()
			.expectStatus().isOk
			.expectBody(String::class.java)
			.isEqualTo("[\"Hello from suspend\"]")
	}

	/**
	 * 12. suspend () -> Flow<R> -> suspendSupplierFlow
	 * Suspending supplier that returns a Flow of Strings.
	 *
	 * --- Input: ---
	 * GET /suspendSupplierFlow
	 *
	 * --- Output: ---
	 * 200 OK
	 * ["x","y","z"]
	 */
	@Test
	fun testSuspendSupplierFlow() {
		webTestClient.get()
			.uri("/suspendSupplierFlow")
			.exchange()
			.expectStatus().isOk
			.expectBody(String::class.java)
			.isEqualTo("[\"x\",\"y\",\"z\"]")
	}

	/**
	 * 13. (T) -> Unit -> consumerSingle
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
	 * 14. (Flow<T>) -> Unit -> consumerFlow
	 * Takes a Flow of Strings (side-effect only).
	 *
	 * --- Input: ---
	 * POST /consumerFlow
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
			.bodyValue(listOf("one", "two"))
			.exchange()
			.expectStatus().isAccepted
	}

	/**
	 * 15. suspend (T) -> Unit -> suspendConsumer
	 * Suspending consumer that takes a String (side-effect only).
	 *
	 * --- Input: ---
	 * POST /suspendConsumer
	 * "test"
	 *
	 * --- Output: ---
	 * 202 ACCEPTED
	 * (No body)
	 */
	@Test
	fun testSuspendConsumer() {
		webTestClient.post()
			.uri("/suspendConsumer")
			.bodyValue("test")
			.exchange()
			.expectStatus().isAccepted
	}

	/**
	 * 16. suspend (Flow<T>) -> Unit -> suspendConsumerFlow
	 * Suspending consumer that takes a Flow of Strings (side-effect only).
	 *
	 * --- Input: ---
	 * POST /suspendConsumerFlow
	 * ["foo","bar"]
	 *
	 * --- Output: ---
	 * 202 ACCEPTED
	 * (No body)
	 */
	@Test
	fun testSuspendConsumerFlow() {
		webTestClient.post()
			.uri("/suspendConsumerFlow")
			.bodyValue(listOf("foo", "bar"))
			.exchange()
			.expectStatus().isAccepted
	}
}
