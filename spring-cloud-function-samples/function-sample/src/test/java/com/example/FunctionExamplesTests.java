package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.cloud.function.context.test.FunctionalSpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Arrays;

/**
 * Test class for verifying the behavior of Spring Cloud Function
 * across various function arities (both reactive and non-reactive).
 */
@FunctionalSpringBootTest
@AutoConfigureWebTestClient
public class FunctionExamplesTests {

	@Autowired
	private WebTestClient webTestClient;

	/**
	 * 1. Test Function<T, R>
	 * Tests the `functionSingleToSingle`, which takes a String and returns its length.
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
	public void testFunctionSingleToSingle() {
		webTestClient.post()
			.uri("/functionSingleToSingle")
			.bodyValue("Hello")
			.exchange()
			.expectStatus().isOk()
			.expectBody(Integer.class)
			.isEqualTo(5);
	}

	/**
	 * 2. Test Function<T, Mono<T>>
	 * Tests the `functionSingleToMono`, which takes a non-reactive String and returns a Mono<String>.
	 *
	 * --- Input: ---
	 * POST /functionSingleToMono
	 * Content-type: application/json
	 * "hello"
	 *
	 * --- Output: ---
	 * Status: 200 OK
	 * "HELLO"
	 */
	@Test
	public void testFunctionSingleToMono() {
		webTestClient.post()
			.uri("/functionSingleToMono")
			.bodyValue("hello")
			.exchange()
			.expectStatus().isOk()
			.expectBody(String.class)
			.isEqualTo("HELLO");
	}

	/**
	 * 3. Test Function<Mono<T>, Mono<R>>
	 * Tests the `functionMonoToMono`, which takes a Mono<String> and returns another Mono<String>.
	 *
	 * --- Input: ---
	 * POST /functionMonoToMono
	 * Content-type: application/json
	 * "hello"
	 *
	 * --- Output: ---
	 * Status: 200 OK
	 * "HELLO"
	 */
	@Test
	public void testFunctionMonoToMono() {
		webTestClient.post()
			.uri("/functionMonoToMono")
			.bodyValue("hello")
			.exchange()
			.expectStatus().isOk()
			.expectBody(String.class)
			.isEqualTo("HELLO");
	}

	/**
	 * 4. Test Function<Mono<T>, Flux<R>>
	 * Tests the `functionMonoToFlux`, which takes a Mono<String> and returns a Flux<String>.
	 *
	 * --- Input: ---
	 * POST /functionMonoToFlux
	 * Content-type: application/json
	 * "test"
	 *
	 * --- Output: ---
	 * Status: 200 OK
	 * ["t", "e", "s", "t"]
	 */
	@Test
	public void testFunctionMonoToFlux() {
		webTestClient.post()
			.uri("/functionMonoToFlux")
			.bodyValue("test")
			.exchange()
			.expectStatus().isOk()
			.expectBody(String.class)
			.isEqualTo("[\"t\",\"e\",\"s\",\"t\"]");
	}

	/**
	 * 5. Test Function<Flux<T>, Mono<R>>
	 * Tests the `functionFluxToMono`, which takes a Flux<String> and returns a Mono<Integer> (count of elements).
	 *
	 * --- Input: ---
	 * POST /functionFluxToMono
	 * Content-type: application/json
	 * ["one", "two", "three"]
	 *
	 * --- Output: ---
	 * Status: 200 OK
	 * 3
	 */
	@Test
	public void testFunctionFluxToMono() {
		webTestClient.post()
			.uri("/functionFluxToMono")
			.bodyValue(Arrays.asList("one", "two", "three"))
			.exchange()
			.expectStatus().isOk()
			.expectBody(Integer.class)
			.isEqualTo(3);
	}

	/**
	 * 6. Test Function<Flux<T>, Flux<R>>
	 * Tests the `functionFluxToFlux`, which takes a Flux<Integer> and returns a Flux<String>.
	 *
	 * --- Input: ---
	 * POST /functionFluxToFlux
	 * Content-type: application/json
	 * [1, 2, 3]
	 *
	 * --- Output: ---
	 * Status: 200 OK
	 * ["1", "2", "3"]
	 */
	@Test
	public void testFunctionFluxToFlux() {
		webTestClient.post()
			.uri("/functionFluxToFlux")
			.bodyValue(Arrays.asList(1, 2, 3))
			.exchange()
			.expectStatus().isOk()
			.expectBody(String.class)
			.isEqualTo("[\"1\",\"2\",\"3\"]");
	}

	/**
	 * 7. Test Function<T, Flux<R>>
	 * Tests the `functionSingleToFlux`, which takes a String and returns a Flux<String> of characters.
	 *
	 * --- Input: ---
	 * POST /functionSingleToFlux
	 * Content-type: application/json
	 * "test"
	 *
	 * --- Output: ---
	 * Status: 200 OK
	 * ["t", "e", "s", "t"]
	 */
	@Test
	public void testFunctionSingleToFlux() {
		webTestClient.post()
			.uri("/functionSingleToFlux")
			.bodyValue("test")
			.exchange()
			.expectStatus().isOk()
			.expectBody(String.class)
			.isEqualTo("[\"t\",\"e\",\"s\",\"t\"]");
	}

	/**
	 * 8. Test Supplier<T>
	 * Tests the `supplierSingle`, which returns a single String.
	 *
	 * --- Input: ---
	 * GET /supplierSingle
	 *
	 * --- Output: ---
	 * Status: 200 OK
	 * "Hello, World!"
	 */
	@Test
	public void testSupplierSingle() {
		webTestClient.get()
			.uri("/supplierSingle")
			.exchange()
			.expectStatus().isOk()
			.expectBody(String.class)
			.isEqualTo("Hello, World!");
	}

	/**
	 * 9. Test Supplier<Mono<T>>
	 * Tests the `supplierMono`, which returns a Mono<String>.
	 *
	 * --- Input: ---
	 * GET /supplierMono
	 *
	 * --- Output: ---
	 * Status: 200 OK
	 * "Hello from Mono!"
	 */
	@Test
	public void testSupplierMono() {
		webTestClient.get()
			.uri("/supplierMono")
			.exchange()
			.expectStatus().isOk()
			.expectBody(String.class)
			.isEqualTo("Hello from Mono!");
	}

	/**
	 * 10. Test Supplier<Flux<T>>
	 * Tests the `supplierFlux`, which returns a Flux<String> stream.
	 *
	 * --- Input: ---
	 * GET /supplierFlux
	 *
	 * --- Output: ---
	 * Status: 200 OK
	 * ["one", "two", "three"]
	 */
	@Test
	public void testSupplierFlux() {
		webTestClient.get()
			.uri("/supplierFlux")
			.exchange()
			.expectStatus().isOk()
			.expectBody(String.class)
			.isEqualTo("[\"one\",\"two\",\"three\"]");
	}

	/**
	 * 11. Test Consumer<T>
	 * Tests the `consumerSingle`, which takes a single String (side-effect only).
	 *
	 * --- Input: ---
	 * POST /consumerSingle
	 * Content-type: application/json
	 * "Some logging data"
	 *
	 * --- Output: ---
	 * Status: 202 ACCEPTED
	 * (No response body, logs the input)
	 */
	@Test
	public void testConsumerSingle() {
		webTestClient.post()
			.uri("/consumerSingle")
			.bodyValue("Some logging data")
			.exchange()
			.expectStatus().isAccepted();
	}

	/**
	 * 12. Test Consumer<Mono<T>>
	 * Tests the `consumerMono`, which takes a Mono<String> (side-effect only).
	 *
	 * --- Input: ---
	 * POST /consumerMono
	 * Content-type: application/json
	 * "Reactive Input"
	 *
	 * --- Output: ---
	 * Status: 202 ACCEPTED
	 * (No response body, logs the input when Mono emits)
	 */
	@Test
	public void testConsumerMono() {
		webTestClient.post()
			.uri("/consumerMono")
			.bodyValue("Reactive Input")
			.exchange()
			.expectStatus().isAccepted();
	}

	/**
	 * 13. Test Consumer<Flux<T>>
	 * Tests the `consumerFlux`, which takes a Flux<String> (side-effect only).
	 *
	 * --- Input: ---
	 * POST /consumerFlux
	 * Content-type: application/json
	 * ["streamed item 1", "streamed item 2"]
	 *
	 * --- Output: ---
	 * Status: 202 ACCEPTED
	 * (No response body, logs each item)
	 */
	@Test
	public void testConsumerFlux() {
		webTestClient.post()
			.uri("/consumerFlux")
			.bodyValue(Arrays.asList("streamed item 1", "streamed item 2"))
			.exchange()
			.expectStatus().isAccepted();
	}
}
