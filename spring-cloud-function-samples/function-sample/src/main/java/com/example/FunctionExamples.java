package com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * ## List of Combinations Tested:
 *
 * 1. **Function<T, R>** - Single input, single output (sync)
 * 2. **Function<T, Mono<T>>** - Single (non-reactive) input, single reactive output
 * 3. **Function<Mono<T>, Mono<R>>** - Mono input, Mono output
 * 4. **Function<Mono<T>, Flux<R>>** - Single reactive input, multiple reactive outputs
 * 5. **Function<Flux<T>, Mono<R>>** - Reactive stream aggregated to a single result
 * 6. **Function<Flux<T>, Flux<R>>** - Reactive stream-to-stream transformation
 * 7. **Function<T, Flux<R>>** - Single input, multiple reactive outputs (streamed)
 * 8. **Supplier<T>** - No input, single value output (sync)
 * 9. **Supplier<Mono<T>>** - No input, single reactive output (Mono)
 * 10. **Supplier<Flux<T>>** - No input, reactive stream of values
 * 11. **Consumer<T>** - Single input, no output (side-effect)
 * 12. **Consumer<Mono<T>>** - Single reactive input (Mono), no output (side-effect)
 * 13. **Consumer<Flux<T>>** - Reactive stream as input, no output
 */
@Configuration
public class FunctionExamples {

	// --- 1. Function<T, R> ---
	/**
	 * Function that takes a String and returns its length as an Integer.
	 *
	 * **Example:**
	 * Input: "Hello"
	 * Output: 5
	 */
	@Bean
	public Function<String, Integer> functionSingleToSingle() {
		return str -> str.length();  // Single input -> single output
	}

	// --- 2. Function<T, Mono<T>> ---
	/**
	 * Function that takes a single (non-reactive) String and returns a Mono<String>.
	 *
	 * **Example:**
	 * Input: "hello"
	 * Output: "HELLO" (wrapped in Mono)
	 */
	@Bean
	public Function<String, Mono<String>> functionSingleToMono() {
		return str -> Mono.just(str.toUpperCase());
	}

	// --- 3. Function<Mono<T>, Mono<R>> ---
	/**
	 * Function that takes a Mono<String> and returns a Mono<String> (uppercase transformation).
	 *
	 * **Example:**
	 * Input: "hello"
	 * Output: "HELLO"
	 */
	@Bean
	public Function<Mono<String>, Mono<String>> functionMonoToMono() {
		return mono -> mono.map(String::toUpperCase);
	}

	// --- 4. Function<Mono<T>, Flux<R>> ---
	/**
	 * Function that takes a single reactive input (Mono<String>) and
	 * returns a Flux<String> of individual characters.
	 *
	 * **Example:**
	 * Input: "test"
	 * Output: ["t", "e", "s", "t"]
	 */
	@Bean
	public Function<Mono<String>, Flux<String>> functionMonoToFlux() {
		return mono -> mono.flatMapMany(str -> Flux.fromArray(str.split("")));
	}

	// --- 5. Function<Flux<T>, Mono<R>> ---
	/**
	 * Function that takes a Flux<String> and returns the number of elements as a Mono<Integer>.
	 *
	 * **Example:**
	 * Input: ["one", "two", "three"]
	 * Output: 3
	 */
	@Bean
	public Function<Flux<String>, Mono<Integer>> functionFluxToMono() {
		return flux -> flux.collectList().map(List::size);
	}

	// --- 6. Function<Flux<T>, Flux<R>> ---
	/**
	 * Function that takes a Flux<Integer> and returns a Flux<String>.
	 *
	 * **Example:**
	 * Input: [1, 2, 3]
	 * Output: ["1", "2", "3"]
	 */
	@Bean
	public Function<Flux<Integer>, Flux<String>> functionFluxToFlux() {
		return flux -> flux.map(Object::toString);
	}

	// --- 7. Function<T, Flux<R>> ---
	/**
	 * Function that takes a String and returns a Flux<String> (stream of characters).
	 *
	 * **Example:**
	 * Input: "test"
	 * Output: ["t", "e", "s", "t"]
	 */
	@Bean
	public Function<String, Flux<String>> functionSingleToFlux() {
		return str -> Flux.fromArray(str.split(""));
	}

	// --- 8. Supplier<T> ---
	/**
	 * Supplier that returns a single String value.
	 *
	 * **Example:**
	 * Output: "Hello, World!"
	 */
	@Bean
	public Supplier<String> supplierSingle() {
		return () -> "Hello, World!";
	}

	// --- 9. Supplier<Mono<T>> ---
	/**
	 * Supplier that returns a single reactive Mono<String>.
	 *
	 * **Example:**
	 * Output: "Hello from Mono!"
	 */
	@Bean
	public Supplier<Mono<String>> supplierMono() {
		return () -> Mono.just("Hello from Mono!");
	}

	// --- 10. Supplier<Flux<T>> ---
	/**
	 * Supplier that returns a reactive stream (Flux<String>) of values.
	 *
	 * **Example:**
	 * Output: ["one", "two", "three"]
	 */
	@Bean
	public Supplier<Flux<String>> supplierFlux() {
		return () -> Flux.just("one", "two", "three");
	}

	// --- 11. Consumer<T> ---
	/**
	 * Consumer that takes a single String and performs a side-effect (logs to console).
	 *
	 * **Example:**
	 * Input: "Some logging data"
	 * Output: (Logs "Received single: Some logging data")
	 */
	@Bean
	public Consumer<String> consumerSingle() {
		return str -> System.out.println("Received single: " + str);
	}

	// --- 12. Consumer<Mono<T>> ---
	/**
	 * Consumer that takes a Mono<String> and performs a side-effect (logs to console).
	 *
	 * **Example:**
	 * Input: "Reactive Input"
	 * Output: (Logs "Received Mono: Reactive Input")
	 */
	@Bean
	public Consumer<Mono<String>> consumerMono() {
		return mono -> mono.subscribe(value -> System.out.println("Received Mono: " + value));
	}

	// --- 13. Consumer<Flux<T>> ---
	/**
	 * Consumer that takes a Flux<String> and performs a side-effect (logs each item).
	 *
	 * **Example:**
	 * Input: ["streamed item 1", "streamed item 2"]
	 * Output: (Logs "Received stream item: streamed item 1", "Received stream item: streamed item 2")
	 */
	@Bean
	public Consumer<Flux<String>> consumerFlux() {
		return flux -> flux.subscribe(str -> System.out.println("Received stream item: " + str));
	}

}
