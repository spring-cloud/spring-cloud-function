/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.cloud.function.integration.dsl;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.PollerSpec;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.endpoint.SourcePollingChannelAdapter;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.integration.test.util.OnlyOnceTrigger;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Artem Bilan
 *
 * @since 4.0.3
 */
@SpringBootTest
@DirtiesContext
public class FunctionFlowTests {

	// To verify cached lookups
	@MockitoSpyBean
	FunctionCatalog functionCatalog;

	@Autowired
	BlockingQueue<String> results;

	@Test
	void fromSupplierOverFunctionToConsumer(@Autowired SourcePollingChannelAdapter supplierEndpoint,
			@Autowired QueueChannel wireTapChannel) throws InterruptedException {

		supplierEndpoint.start();

		String result = this.results.poll(10, TimeUnit.SECONDS);
		assertThat(result).isEqualTo("SIMPLE TEST DATA");
		Message<?> receive = wireTapChannel.receive(10_000);
		assertThat(receive)
				.extracting(Message::getPayload)
				.isEqualTo("simple test data".getBytes());

		supplierEndpoint.stop();
	}

	@Test
	void fromChannelToFunctionComposition(@Autowired MessageChannel functionCompositionInput)
			throws InterruptedException {

		functionCompositionInput.send(new GenericMessage<>("compose this"));

		String result = this.results.poll(10, TimeUnit.SECONDS);
		assertThat(result).isEqualTo("COMPOSE THIS");

		functionCompositionInput.send(new GenericMessage<>("compose again"));

		result = this.results.poll(10, TimeUnit.SECONDS);
		assertThat(result).isEqualTo("COMPOSE AGAIN");

		// Ensure that FunctionLookupHelper.memoize() does its trick calling FunctionCatalog.lookup() only once
		verify(this.functionCatalog).lookup(Consumer.class, "upperCaseFunction|simpleStringConsumer");
	}

	@Test
	void noFunctionInCatalogException(@Autowired IntegrationFlowContext integrationFlowContext) {
		// We need to mock here since BeanFactoryAwareFunctionRegistry will have slightly different logic
		FunctionCatalog mockFunctionCatalog = mock(FunctionCatalog.class);

		FunctionFlowBuilder functionFlowBuilder = new FunctionFlowBuilder(mockFunctionCatalog);

		IntegrationFlow wrongFlow =
				functionFlowBuilder.from("inputChannel")
						.accept("nonExistingConsumer");

		IntegrationFlowContext.IntegrationFlowRegistration registration =
				integrationFlowContext.registration(wrongFlow)
						.register();

		assertThatExceptionOfType(MessageDeliveryException.class)
				.isThrownBy(() -> registration.getInputChannel().send(new GenericMessage<>("test")))
				.withRootCauseInstanceOf(IllegalArgumentException.class)
				.withStackTraceContaining("No 'nonExistingConsumer' in the catalog");

		registration.destroy();
	}


	@EnableAutoConfiguration
	@Configuration(proxyBeanMethods = false)
	static class TestIntegrationConfiguration {

		@Bean(PollerMetadata.DEFAULT_POLLER)
		PollerSpec defaultPoller() {
			return Pollers.trigger(new OnlyOnceTrigger());
		}

		@Bean
		Supplier<byte[]> simpleByteArraySupplier() {
			return "simple test data"::getBytes;
		}

		@Bean
		Function<String, String> upperCaseFunction() {
			return String::toUpperCase;
		}

		@Bean
		BlockingQueue<String> results() {
			return new LinkedBlockingQueue<>();
		}

		@Bean
		Consumer<String> simpleStringConsumer(BlockingQueue<String> results) {
			return results::add;
		}

		@Bean
		QueueChannel wireTapChannel() {
			return new QueueChannel();
		}

		@Bean
		IntegrationFlow someFunctionFlow(FunctionFlowBuilder functionFlowBuilder) {
			return functionFlowBuilder
					.fromSupplier("simpleByteArraySupplier", e -> e.id("supplierEndpoint").autoStartup(false))
					.wireTap("wireTapChannel")
					.apply("upperCaseFunction")
					.log(LoggingHandler.Level.WARN, FunctionFlowTests.class.getName())
					.accept("simpleStringConsumer");
		}

		@Bean
		IntegrationFlow functionCompositionFlow(FunctionFlowBuilder functionFlowBuilder) {
			return functionFlowBuilder
					.from("functionCompositionInput")
					.accept("upperCaseFunction|simpleStringConsumer");
		}

	}

}
