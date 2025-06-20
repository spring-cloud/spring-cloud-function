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

package org.springframework.cloud.function.kotlin;

import java.util.function.Function;

import kotlin.jvm.functions.Function1;
import kotlinx.coroutines.flow.Flow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.kotlin.arity.KotlinArityApplication;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to verify that Kotlin Function implementations can be cast to native Java functional interfaces,
 * invoked properly, and produce correct results.
 *
 * @author AI Assistant
 */
public class KotlinFunctionArityNativeCastTests {

	private GenericApplicationContext context;

	private FunctionCatalog catalog;

	@AfterEach
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	/**
	 * Test that plain functions from KotlinFunctionArityBean, KotlinFunctionArityComponent, and KotlinFunctionArityJava
	 * can be cast to java.util.function.Function and invoked.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"functionPlainToPlain", "functionKotlinPlainToPlain", "functionJavaPlainToPlain"
	})
	public void testPlainFunctionsCastToNative(String functionName) {
		create(new Class[] {KotlinArityApplication.class});

		FunctionInvocationWrapper wrapper = this.catalog.lookup(functionName);
		assertThat(wrapper).as("Function not found: " + functionName).isNotNull();

		// Get the actual function bean
		Object functionBean = wrapper.getTarget();
		assertThat(functionBean).isNotNull();

		// Cast to java.util.function.Function
		@SuppressWarnings("unchecked")
		Function<String, Integer> function = (Function<String, Integer>) functionBean;

		// Invoke the function
		Integer result = function.apply("test-native-cast");

		// Verify the result (should be the length of the input string)
		assertThat(result).isEqualTo("test-native-cast".length());
	}

	/**
	 * Test that functions returning Mono can be cast to java.util.function.Function and invoked.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"functionPlainToMono", "functionKotlinPlainToMono", "functionJavaPlainToMono"
	})
	public void testMonoFunctionsCastToNative(String functionName) {
		create(new Class[] {KotlinArityApplication.class});

		FunctionInvocationWrapper wrapper = this.catalog.lookup(functionName);
		assertThat(wrapper).as("Function not found: " + functionName).isNotNull();

		// Get the actual function bean
		Object functionBean = wrapper.getTarget();
		assertThat(functionBean).isNotNull();

		// Cast to java.util.function.Function
		@SuppressWarnings("unchecked")
		Function<String, Mono<Integer>> function = (Function<String, Mono<Integer>>) functionBean;

		// Invoke the function
		Mono<Integer> result = function.apply("test-mono");

		// Verify the result
		Integer value = result.block();
		assertThat(value).isEqualTo("test-mono".length());
	}

	/**
	 * Test that functions returning Flux can be cast to java.util.function.Function and invoked.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"functionPlainToFlux", "functionKotlinPlainToFlux", "functionJavaPlainToFlux"
	})
	public void testFluxFunctionsCastToNative(String functionName) {
		create(new Class[] {KotlinArityApplication.class});

		FunctionInvocationWrapper wrapper = this.catalog.lookup(functionName);
		assertThat(wrapper).as("Function not found: " + functionName).isNotNull();

		// Get the actual function bean
		Object functionBean = wrapper.getTarget();
		assertThat(functionBean).isNotNull();

		// Cast to java.util.function.Function
		@SuppressWarnings("unchecked")
		Function<String, Flux<String>> function = (Function<String, Flux<String>>) functionBean;

		// Invoke the function
		Flux<String> result = function.apply("abc");

		// Verify the result
		assertThat(result.collectList().block()).containsExactly("a", "b", "c");
	}

	/**
	 * Test that Mono to Mono functions can be cast to java.util.function.Function and invoked.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"functionMonoToMono", "functionKotlinMonoToMono", "functionJavaMonoToMono"
	})
	public void testMonoToMonoFunctionsCastToNative(String functionName) {
		create(new Class[] {KotlinArityApplication.class});

		FunctionInvocationWrapper wrapper = this.catalog.lookup(functionName);
		assertThat(wrapper).as("Function not found: " + functionName).isNotNull();

		// Get the actual function bean
		Object functionBean = wrapper.getTarget();
		assertThat(functionBean).isNotNull();

		// Cast to java.util.function.Function
		@SuppressWarnings("unchecked")
		Function<Mono<String>, Mono<String>> function = (Function<Mono<String>, Mono<String>>) functionBean;

		// Invoke the function
		Mono<String> result = function.apply(Mono.just("test-mono-to-mono"));

		// Verify the result
		String value = result.block();
		assertThat(value).isEqualTo("TEST-MONO-TO-MONO");
	}

	/**
	 * Test that Flux to Flux functions can be cast to java.util.function.Function and invoked.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"functionFluxToFlux", "functionKotlinFluxToFlux", "functionJavaFluxToFlux"
	})
	public void testFluxToFluxFunctionsCastToNative(String functionName) {
		create(new Class[] {KotlinArityApplication.class});

		FunctionInvocationWrapper wrapper = this.catalog.lookup(functionName);
		assertThat(wrapper).as("Function not found: " + functionName).isNotNull();

		// Get the actual function bean
		Object functionBean = wrapper.getTarget();
		assertThat(functionBean).isNotNull();

		// Cast to java.util.function.Function
		@SuppressWarnings("unchecked")
		Function<Flux<String>, Flux<Integer>> function = (Function<Flux<String>, Flux<Integer>>) functionBean;

		// Invoke the function
		Flux<Integer> result = function.apply(Flux.just("a", "bb", "ccc"));

		// Verify the result
		assertThat(result.collectList().block()).containsExactly(1, 2, 3);
	}

	/**
	 * Test that Message to Message functions can be cast to java.util.function.Function and invoked.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"functionMessageToMessage", "functionKotlinMessageToMessage", "functionJavaMessageToMessage"
	})
	public void testMessageToMessageFunctionsCastToNative(String functionName) {
		create(new Class[] {KotlinArityApplication.class});

		FunctionInvocationWrapper wrapper = this.catalog.lookup(functionName);
		assertThat(wrapper).as("Function not found: " + functionName).isNotNull();

		// Get the actual function bean
		Object functionBean = wrapper.getTarget();
		assertThat(functionBean).isNotNull();

		// Cast to java.util.function.Function
		@SuppressWarnings("unchecked")
		Function<Message<String>, Message<Integer>> function = (Function<Message<String>, Message<Integer>>) functionBean;

		// Create a message and invoke the function
		Message<String> message = MessageBuilder.withPayload("test-message").build();
		Message<Integer> result = function.apply(message);

		// Verify the result
		assertThat(result.getPayload()).isEqualTo("test-message".length());
		assertThat(result.getHeaders().get("processed")).isEqualTo("true");
	}

	/**
	 * Test that Mono&lt;Message&gt; to Mono&lt;Message&gt; functions can be cast to java.util.function.Function and invoked.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"functionMonoMessageToMonoMessage", "functionKotlinMonoMessageToMonoMessage", "functionJavaMonoMessageToMonoMessage"
	})
	public void testMonoMessageToMonoMessageFunctionsCastToNative(String functionName) {
		create(new Class[] {KotlinArityApplication.class});

		FunctionInvocationWrapper wrapper = this.catalog.lookup(functionName);
		assertThat(wrapper).as("Function not found: " + functionName).isNotNull();

		// Get the actual function bean
		Object functionBean = wrapper.getTarget();
		assertThat(functionBean).isNotNull();

		// Cast to java.util.function.Function
		@SuppressWarnings("unchecked")
		Function<Mono<Message<String>>, Mono<Message<Integer>>> function =
			(Function<Mono<Message<String>>, Mono<Message<Integer>>>) functionBean;

		// Create a message and invoke the function
		Message<String> message = MessageBuilder.withPayload("test-mono-message").build();
		Mono<Message<Integer>> result = function.apply(Mono.just(message));

		// Verify the result
		Message<Integer> resultMessage = result.block();
		assertThat(resultMessage).isNotNull();
		assertThat(resultMessage.getHeaders().get("mono-processed")).isEqualTo("true");
	}

	/**
	 * Test that Flux&lt;Message&gt; to Flux&lt;Message&gt; functions can be cast to java.util.function.Function and invoked.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"functionFluxMessageToFluxMessage", "functionKotlinFluxMessageToFluxMessage", "functionJavaFluxMessageToFluxMessage"
	})
	public void testFluxMessageToFluxMessageFunctionsCastToNative(String functionName) {
		create(new Class[] {KotlinArityApplication.class});

		FunctionInvocationWrapper wrapper = this.catalog.lookup(functionName);
		assertThat(wrapper).as("Function not found: " + functionName).isNotNull();

		// Get the actual function bean
		Object functionBean = wrapper.getTarget();
		assertThat(functionBean).isNotNull();

		// Cast to java.util.function.Function
		@SuppressWarnings("unchecked")
		Function<Flux<Message<String>>, Flux<Message<String>>> function =
			(Function<Flux<Message<String>>, Flux<Message<String>>>) functionBean;

		// Create messages and invoke the function
		Message<String> message1 = MessageBuilder.withPayload("test1").build();
		Message<String> message2 = MessageBuilder.withPayload("test2").build();
		Flux<Message<String>> result = function.apply(Flux.just(message1, message2));

		// Verify the result
		assertThat(result.collectList().block()).hasSize(2);
		assertThat(result.map(msg -> msg.getPayload()).collectList().block())
			.containsExactly("TEST1", "TEST2");
	}

	/**
	 * Test that Flow&lt;Message&gt; to Flow&lt;Message&gt; functions can be cast to java.util.function.Function.
	 * Note: We can't easily invoke Flow functions directly in Java, but we can verify the cast works.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"functionFlowMessageToFlowMessage", "functionKotlinFlowMessageToFlowMessage", "functionJavaFlowMessageToFlowMessage"
	})
	public void testFlowMessageToFlowMessageFunctionsCastToNative(String functionName) {
		create(new Class[] {KotlinArityApplication.class});

		FunctionInvocationWrapper wrapper = this.catalog.lookup(functionName);
		assertThat(wrapper).as("Function not found: " + functionName).isNotNull();

		// Get the actual function bean
		Object functionBean = wrapper.getTarget();
		assertThat(functionBean).isNotNull();

		// Cast to java.util.function.Function
		@SuppressWarnings("unchecked")
		Function<Flow<Message<String>>, Flow<Message<String>>> function =
			(Function<Flow<Message<String>>, Flow<Message<String>>>) functionBean;

		// Verify the cast works
		assertThat(function).isNotNull();
	}

	/**
	 * Test that plain functions can be cast to kotlin.jvm.functions.Function1 and invoked.
	 * This verifies that Function implementations also implement the Kotlin Function1 interface.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"functionPlainToPlain", "functionKotlinPlainToPlain"
	})
	public void testPlainFunctionsCastToKotlinFunction1(String functionName) {
		create(new Class[] {KotlinArityApplication.class});

		FunctionInvocationWrapper wrapper = this.catalog.lookup(functionName);
		assertThat(wrapper).as("Function not found: " + functionName).isNotNull();

		// Get the actual function bean
		Object functionBean = wrapper.getTarget();
		assertThat(functionBean).isNotNull();

		// Cast to kotlin.jvm.functions.Function1
		@SuppressWarnings("unchecked")
		Function1<String, Integer> function = (Function1<String, Integer>) functionBean;

		// Invoke the function
		Integer result = function.invoke("test-kotlin-cast");

		// Verify the result (should be the length of the input string)
		assertThat(result).isEqualTo("test-kotlin-cast".length());
	}

	/**
	 * Test that functions returning Mono can be cast to kotlin.jvm.functions.Function1 and invoked.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"functionPlainToMono", "functionKotlinPlainToMono"
	})
	public void testMonoFunctionsCastToKotlinFunction1(String functionName) {
		create(new Class[] {KotlinArityApplication.class});

		FunctionInvocationWrapper wrapper = this.catalog.lookup(functionName);
		assertThat(wrapper).as("Function not found: " + functionName).isNotNull();

		// Get the actual function bean
		Object functionBean = wrapper.getTarget();
		assertThat(functionBean).isNotNull();

		// Cast to kotlin.jvm.functions.Function1
		@SuppressWarnings("unchecked")
		Function1<String, Mono<Integer>> function = (Function1<String, Mono<Integer>>) functionBean;

		// Invoke the function
		Mono<Integer> result = function.invoke("test-kotlin-mono");

		// Verify the result
		Integer value = result.block();
		assertThat(value).isEqualTo("test-kotlin-mono".length());
	}

	/**
	 * Test that functions returning Flux can be cast to kotlin.jvm.functions.Function1 and invoked.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"functionPlainToFlux", "functionKotlinPlainToFlux"
	})
	public void testFluxFunctionsCastToKotlinFunction1(String functionName) {
		create(new Class[] {KotlinArityApplication.class});

		FunctionInvocationWrapper wrapper = this.catalog.lookup(functionName);
		assertThat(wrapper).as("Function not found: " + functionName).isNotNull();

		// Get the actual function bean
		Object functionBean = wrapper.getTarget();
		assertThat(functionBean).isNotNull();

		// Cast to kotlin.jvm.functions.Function1
		@SuppressWarnings("unchecked")
		Function1<String, Flux<String>> function = (Function1<String, Flux<String>>) functionBean;

		// Invoke the function
		Flux<String> result = function.invoke("abc");

		// Verify the result
		assertThat(result.collectList().block()).containsExactly("a", "b", "c");
	}

	/**
	 * Test that Message to Message functions can be cast to kotlin.jvm.functions.Function1 and invoked.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"functionMessageToMessage", "functionKotlinMessageToMessage"
	})
	public void testMessageFunctionsCastToKotlinFunction1(String functionName) {
		create(new Class[] {KotlinArityApplication.class});

		FunctionInvocationWrapper wrapper = this.catalog.lookup(functionName);
		assertThat(wrapper).as("Function not found: " + functionName).isNotNull();

		// Get the actual function bean
		Object functionBean = wrapper.getTarget();
		assertThat(functionBean).isNotNull();

		// Cast to kotlin.jvm.functions.Function1
		@SuppressWarnings("unchecked")
		Function1<Message<String>, Message<Integer>> function = (Function1<Message<String>, Message<Integer>>) functionBean;

		// Create a message and invoke the function
		Message<String> message = MessageBuilder.withPayload("test-kotlin-message").build();
		Message<Integer> result = function.invoke(message);

		// Verify the result
		assertThat(result.getPayload()).isEqualTo("test-kotlin-message".length());
		assertThat(result.getHeaders().get("processed")).isEqualTo("true");
	}

	private void create(Class<?>[] types, String... props) {
		this.context = (GenericApplicationContext) new SpringApplicationBuilder(types).properties(props).run();
		this.catalog = this.context.getBean(FunctionCatalog.class);
	}
}
