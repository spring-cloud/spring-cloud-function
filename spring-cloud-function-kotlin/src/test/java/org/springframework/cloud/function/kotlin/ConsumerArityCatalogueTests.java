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
 * Tests for different arity functions, suppliers, and consumers in the FunctionCatalog.
 *
 * @author Adrien Poupard
 */
public class ConsumerArityCatalogueTests {

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
		"consumerPlain", "consumerKotlinPlain", "consumerJavaPlain"
	})
	public void testPlainConsumers(String consumerName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper consumer = this.catalog.lookup(consumerName);

		// Test should fail if consumer is not found
		assertThat(consumer).as("Consumer not found: " + consumerName).isNotNull();

		// Verify it's a consumer
		assertThat(consumer.isConsumer()).isTrue();

		// Plain string consumer
		String typeName = consumer.getInputType().getTypeName();
		assertThat(typeName).isEqualTo("java.lang.String");

		// Just verifying it doesn't throw an exception
		consumer.apply("test");
	}

	@ParameterizedTest
	@ValueSource(strings = {"consumerSuspendPlain", "consumerKotlinSuspendPlain"})
	public void testSuspendPlainConsumers(String consumerName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper consumer = this.catalog.lookup(consumerName);

		// Test should fail if consumer is not found
		assertThat(consumer).as("Consumer not found: " + consumerName).isNotNull();

		// Verify it's a consumer
		assertThat(consumer.isConsumer()).isTrue();

		String typeName = consumer.getInputType().getTypeName();

		// Suspend Plain consumer
		assertThat(typeName).isEqualTo("java.lang.String");
		consumer.apply("test");
	}

	@ParameterizedTest
	@ValueSource(strings = {"consumerJavaFlow", "consumerKotlinFlow", "consumerFlow"})
	public void testFlowConsumerMethods(String consumerName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper consumer = this.catalog.lookup(consumerName);

		// Test should fail if consumer is not found
		assertThat(consumer).as("Consumer not found: " + consumerName).isNotNull();

		// Verify it's a consumer
		assertThat(consumer.isConsumer()).isTrue();

		String typeName = consumer.getInputType().getTypeName();

		// Flow consumer
		// Note: Spring Cloud Function might convert Kotlin Flow to Reactor Flux
		assertThat(typeName.contains("Flux")).isTrue();
		// We can't easily create a Flow instance for testing, so we just verify the type
	}

	@ParameterizedTest
	@ValueSource(strings = {"consumerMonoInput", "consumerJavaMonoInput", "consumerKotlinMonoInput"})
	public void testMonoInputConsumers(String consumerName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper consumer = this.catalog.lookup(consumerName);

		// Test should fail if consumer is not found
		assertThat(consumer).as("Consumer not found: " + consumerName).isNotNull();

		// Verify it's a consumer
		assertThat(consumer.isConsumer()).isTrue();

		String typeName = consumer.getInputType().getTypeName();

		// MonoInput consumer (actually a String consumer that returns Mono<Void>)
		assertThat(typeName).isEqualTo("java.lang.String");
		consumer.apply("test");
	}

	@ParameterizedTest
	@ValueSource(strings = {"consumerMono", "consumerJavaMono", "consumerKotlinMono"})
	public void testMonoConsumers(String consumerName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper consumer = this.catalog.lookup(consumerName);

		// Test should fail if consumer is not found
		assertThat(consumer).as("Consumer not found: " + consumerName).isNotNull();

		// Verify it's a consumer
		assertThat(consumer.isConsumer()).isTrue();

		String typeName = consumer.getInputType().getTypeName();

		// Mono consumer
		assertThat(typeName).contains("Mono");
		// We can't easily test the actual consumption of a Mono, so we just verify the type
	}

	@ParameterizedTest
	@ValueSource(strings = {"consumerFlux", "consumerJavaFlux", "consumerKotlinFlux"})
	public void testFluxConsumers(String consumerName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper consumer = this.catalog.lookup(consumerName);

		// Test should fail if consumer is not found
		assertThat(consumer).as("Consumer not found: " + consumerName).isNotNull();

		// Verify it's a consumer
		assertThat(consumer.isConsumer()).isTrue();

		String typeName = consumer.getInputType().getTypeName();

		// Flux consumer
		assertThat(typeName).contains("Flux");
		// We can't easily test the actual consumption of a Flux, so we just verify the type
	}

	@ParameterizedTest
	@ValueSource(strings = {"consumerMessage", "consumerJavaMessage", "consumerKotlinMessage"})
	public void testMessageConsumers(String consumerName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper consumer = this.catalog.lookup(consumerName);

		// Test should fail if consumer is not found
		assertThat(consumer).as("Consumer not found: " + consumerName).isNotNull();

		// Verify it's a consumer
		assertThat(consumer.isConsumer()).isTrue();

		String typeName = consumer.getInputType().getTypeName();

		// Message consumer
		assertThat(typeName).contains("Message");
		Message<String> message = MessageBuilder.withPayload("test").build();
		consumer.apply(message);
	}

	@ParameterizedTest
	@ValueSource(strings = {"consumerMonoMessage", "consumerJavaMonoMessage", "consumerKotlinMonoMessage"})
	public void testMonoMessageConsumers(String consumerName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper consumer = this.catalog.lookup(consumerName);

		// Test should fail if consumer is not found
		assertThat(consumer).as("Consumer not found: " + consumerName).isNotNull();

		// Verify it's a consumer
		assertThat(consumer.isConsumer()).isTrue();

		String typeName = consumer.getInputType().getTypeName();

		// Mono<Message> consumer
		assertThat(typeName).contains("Mono");
		assertThat(typeName).contains("Message");
		// We can't easily test the actual consumption of a Mono<Message>, so we just verify the type
	}

	@ParameterizedTest
	@ValueSource(strings = {"consumerSuspendMessage", "consumerKotlinSuspendMessage"})
	public void testSuspendMessageConsumers(String consumerName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper consumer = this.catalog.lookup(consumerName);

		// Test should fail if consumer is not found
		assertThat(consumer).as("Consumer not found: " + consumerName).isNotNull();

		// Verify it's a consumer
		assertThat(consumer.isConsumer()).isTrue();

		String typeName = consumer.getInputType().getTypeName();

		// Suspend Message consumer
		assertThat(typeName).contains("Message");
		Message<String> message = MessageBuilder.withPayload("test").build();
		consumer.apply(message);
	}

	@ParameterizedTest
	@ValueSource(strings = {"consumerFluxMessage", "consumerJavaFluxMessage", "consumerKotlinFluxMessage"})
	public void testFluxMessageConsumers(String consumerName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper consumer = this.catalog.lookup(consumerName);

		// Test should fail if consumer is not found
		assertThat(consumer).as("Consumer not found: " + consumerName).isNotNull();

		// Verify it's a consumer
		assertThat(consumer.isConsumer()).isTrue();

		String typeName = consumer.getInputType().getTypeName();

		// Flux<Message> consumer
		assertThat(typeName).contains("Flux");
		assertThat(typeName).contains("Message");
	}

	@ParameterizedTest
	@ValueSource(strings = {"consumerSuspendFlowMessage", "consumerKotlinSuspendFlowMessage"})
	public void testSuspendFlowMessageConsumers(String consumerName) {
		create(new Class[] { KotlinArityApplication.class });

		FunctionInvocationWrapper consumer = this.catalog.lookup(consumerName);

		// Test should fail if consumer is not found
		assertThat(consumer).as("Consumer not found: " + consumerName).isNotNull();

		// Verify it's a consumer
		assertThat(consumer.isConsumer()).isTrue();

		String typeName = consumer.getInputType().getTypeName();

		assertThat(typeName.contains("Flux")).isTrue();
		assertThat(typeName).contains("Message");
	}

	private void create(Class<?>[] types, String... props) {
		this.context = (GenericApplicationContext) new SpringApplicationBuilder(types).properties(props).run();
		this.catalog = this.context.getBean(FunctionCatalog.class);
	}
}
