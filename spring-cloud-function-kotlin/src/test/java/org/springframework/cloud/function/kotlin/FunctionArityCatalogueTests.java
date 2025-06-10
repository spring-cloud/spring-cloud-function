/*
 * Copyright 2019-2021 the original author or authors.
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.kotlin.arity.KotlinArityApplication;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for different arity functions in the FunctionCatalog.
 *
 * @author Adrien Poupard
 */
public class FunctionArityCatalogueTests {

	private GenericApplicationContext context;

	private FunctionCatalog catalog;

	@AfterEach
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionPlainToPlain", "functionJavaPlainToPlain", "functionKotlinPlainToPlain"
	})
	public void testPlainToPlainFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Plain string to int function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		assertThat(inputTypeName).isEqualTo("java.lang.String");
		assertThat(outputTypeName).isEqualTo("java.lang.Integer");

		// Verify function execution
		Object result = function.apply("test");
		assertThat(result).isEqualTo(4); // "test".length() == 4
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionPlainToFlow", "functionJavaPlainToFlow", "functionKotlinPlainToFlow"
	})
	public void testPlainToFlowFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Plain string to flow function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		assertThat(inputTypeName).isEqualTo("java.lang.String");
		// Output might be Flow or Flux depending on how Spring Cloud Function handles Kotlin types
		assertThat(outputTypeName.contains("Flux")).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionFlowToPlain", "functionJavaFlowToPlain", "functionKotlinFlowToPlain"
	})
	public void testFlowToPlainFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Flow to plain function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		// Input might be Flow or Flux depending on how Spring Cloud Function handles Kotlin types
		assertThat(inputTypeName.contains("Flux")).isTrue();
		assertThat(outputTypeName).isEqualTo("java.lang.Integer");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionFlowToFlow", "functionJavaFlowToFlow", "functionKotlinFlowToFlow"
	})
	public void testFlowToFlowFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Flow to flow function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		// Input and output might be Flow or Flux depending on how Spring Cloud Function handles Kotlin types
		assertThat(inputTypeName.contains("Flux")).isTrue();
		assertThat(outputTypeName.contains("Flux")).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionSuspendPlainToPlain", "functionKotlinSuspendPlainToPlain"
	})
	public void testSuspendPlainToPlainFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Suspend plain to plain function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		assertThat(inputTypeName).isEqualTo("java.lang.String");
		// Suspend functions are wrapped in Flux by Spring Cloud Function
		assertThat(outputTypeName.contains("Flux")).isTrue();
		assertThat(outputTypeName.contains("Integer")).isTrue();

		// Verify function execution
		Object result = function.apply("test");
		// Result is a Flux, so we can't directly assert on the value
		assertThat(result.toString()).contains("Flux");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionSuspendPlainToFlow", "functionKotlinSuspendPlainToFlow"
	})
	public void testSuspendPlainToFlowFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Suspend plain to flow function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		assertThat(inputTypeName).isEqualTo("java.lang.String");
		// Output might be Flow or Flux depending on how Spring Cloud Function handles Kotlin types
		assertThat(outputTypeName.contains("Flux")).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionSuspendFlowToPlain", "functionKotlinSuspendFlowToPlain"
	})
	public void testSuspendFlowToPlainFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Suspend flow to plain function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		// Input might be Flow or Flux depending on how Spring Cloud Function handles Kotlin types
		assertThat(inputTypeName.contains("Flux")).isTrue();
		assertThat(outputTypeName).isEqualTo("java.lang.Integer");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionSuspendFlowToFlow", "functionKotlinSuspendFlowToFlow"
	})
	public void testSuspendFlowToFlowFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Suspend flow to flow function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		// Input and output might be Flow or Flux depending on how Spring Cloud Function handles Kotlin types
		assertThat(inputTypeName.contains("Flux")).isTrue();
		assertThat(outputTypeName.contains("Flux")).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionPlainToMono", "functionJavaPlainToMono", "functionKotlinPlainToMono"
	})
	public void testPlainToMonoFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Plain to mono function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		assertThat(inputTypeName).isEqualTo("java.lang.String");
		assertThat(outputTypeName.contains("Mono")).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionPlainToFlux", "functionJavaPlainToFlux", "functionKotlinPlainToFlux"
	})
	public void testPlainToFluxFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Plain to flux function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		assertThat(inputTypeName).isEqualTo("java.lang.String");
		assertThat(outputTypeName.contains("Flux")).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionMonoToMono", "functionJavaMonoToMono", "functionKotlinMonoToMono"
	})
	public void testMonoToMonoFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Mono to mono function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		assertThat(inputTypeName.contains("Mono")).isTrue();
		assertThat(outputTypeName.contains("Mono")).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionFluxToFlux", "functionJavaFluxToFlux", "functionKotlinFluxToFlux"
	})
	public void testFluxToFluxFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Flux to flux function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		assertThat(inputTypeName.contains("Flux")).isTrue();
		assertThat(outputTypeName.contains("Flux")).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionFluxToMono", "functionJavaFluxToMono", "functionKotlinFluxToMono"
	})
	public void testFluxToMonoFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Flux to mono function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		assertThat(inputTypeName.contains("Flux")).isTrue();
		assertThat(outputTypeName.contains("Mono")).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionMessageToMessage", "functionJavaMessageToMessage", "functionKotlinMessageToMessage"
	})
	public void testMessageToMessageFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Message to message function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		assertThat(inputTypeName.contains("Message")).isTrue();
		assertThat(outputTypeName.contains("Message")).isTrue();

		// Verify function execution with a message
		Message<String> message = MessageBuilder.withPayload("test").build();
		Object result = function.apply(message);
		assertThat(result).isInstanceOf(Message.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionSuspendMessageToMessage", "functionKotlinSuspendMessageToMessage"
	})
	public void testSuspendMessageToMessageFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Suspend message to message function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		assertThat(inputTypeName.contains("Message")).isTrue();
		// Suspend functions are wrapped in Flux by Spring Cloud Function
		assertThat(outputTypeName.contains("Flux")).isTrue();
		assertThat(outputTypeName.contains("Message")).isTrue();

		// Verify function execution with a message
		Message<String> message = MessageBuilder.withPayload("test").build();
		Object result = function.apply(message);
		// Result is a Flux, so we can't directly assert it's a Message
		assertThat(result.toString()).contains("Flux");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionMonoMessageToMonoMessage", "functionJavaMonoMessageToMonoMessage", "functionKotlinMonoMessageToMonoMessage"
	})
	public void testMonoMessageToMonoMessageFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Mono message to mono message function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		assertThat(inputTypeName.contains("Mono")).isTrue();
		assertThat(inputTypeName.contains("Message")).isTrue();
		assertThat(outputTypeName.contains("Mono")).isTrue();
		assertThat(outputTypeName.contains("Message")).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionFluxMessageToFluxMessage", "functionJavaFluxMessageToFluxMessage", "functionKotlinFluxMessageToFluxMessage"
	})
	public void testFluxMessageToFluxMessageFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Flux message to flux message function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		assertThat(inputTypeName.contains("Flux")).isTrue();
		assertThat(inputTypeName.contains("Message")).isTrue();
		assertThat(outputTypeName.contains("Flux")).isTrue();
		assertThat(outputTypeName.contains("Message")).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionFlowMessageToFlowMessage", "functionJavaFlowMessageToFlowMessage", "functionKotlinFlowMessageToFlowMessage"
	})
	public void testFlowMessageToFlowMessageFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Flow message to flow message function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		// Input and output might be Flow or Flux depending on how Spring Cloud Function handles Kotlin types
		assertThat(inputTypeName.contains("Flux")).isTrue();
		assertThat(inputTypeName.contains("Message")).isTrue();
		assertThat(outputTypeName.contains("Flux")).isTrue();
		assertThat(outputTypeName.contains("Message")).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"functionSuspendFlowMessageToFlowMessage", "functionKotlinSuspendFlowMessageToFlowMessage"
	})
	public void testSuspendFlowMessageToFlowMessageFunctions(String functionName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper function = this.catalog.lookup(functionName);

		// Test should fail if function is not found
		assertThat(function).as("Function not found: " + functionName).isNotNull();

		// Verify it's a function
		assertThat(function.isFunction()).isTrue();

		// Suspend flow message to flow message function
		String inputTypeName = function.getInputType().getTypeName();
		String outputTypeName = function.getOutputType().getTypeName();
		// Input and output might be Flow or Flux depending on how Spring Cloud Function handles Kotlin types
		assertThat(inputTypeName.contains("Flux")).isTrue();
		assertThat(inputTypeName.contains("Message")).isTrue();
		assertThat(outputTypeName.contains("Flux")).isTrue();
		assertThat(outputTypeName.contains("Message")).isTrue();
	}

	private void create(Class<?>[] types, String... props) {
		this.context = (GenericApplicationContext) new SpringApplicationBuilder(types).properties(props).run();
		this.catalog = this.context.getBean(FunctionCatalog.class);
	}
}
