/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.function.rsocket;


import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.DestinationPatternsMessageCondition;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
public class RSocketAutoConfigurationRoutingTests {
	@Test
	public void testRoutingWithRoute() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
							"--spring.cloud.function.routing-expression=headers.func_name",
							"--spring.cloud.function.expected-content-type=text/plain",
							"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("uppercase")
				.metadata("{\"func_name\":\"echo\"}", MimeTypeUtils.APPLICATION_JSON)
				.data("hello")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("hello")
				.expectComplete()
				.verify();

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("")
				.metadata("{\"func_name\":\"echo\"}", MimeTypeUtils.APPLICATION_JSON)
				.data("hello")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("hello")
				.expectComplete()
				.verify();

			rsocketRequesterBuilder.tcp("localhost", port)
				.route(RoutingFunction.FUNCTION_NAME)
				.metadata("{\"func_name\":\"echo\"}", MimeTypeUtils.APPLICATION_JSON)
				.data("hello")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("hello")
				.expectComplete()
				.verify();

		}
	}

	@Test
	public void testRoutingWithDefinition() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
							"--spring.cloud.function.definition=uppercase",
							"--spring.cloud.function.routing-expression=headers.func_name",
							"--spring.cloud.function.expected-content-type=text/plain",
							"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("uppercase")
				.metadata("{\"func_name\":\"echo\"}", MimeTypeUtils.APPLICATION_JSON)
				.data("hello")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("hello")
				.expectComplete()
				.verify();

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("")
				.metadata("{\"func_name\":\"echo\"}", MimeTypeUtils.APPLICATION_JSON)
				.data("hello")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("hello")
				.expectComplete()
				.verify();

			rsocketRequesterBuilder.tcp("localhost", port)
				.route(RoutingFunction.FUNCTION_NAME)
				.metadata("{\"func_name\":\"echo\"}", MimeTypeUtils.APPLICATION_JSON)
				.data("hello")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("hello")
				.expectComplete()
				.verify();

		}
	}

	@Test
	public void testRoutingWithDefinitionMessageFunction() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
							"--spring.cloud.function.definition=uppercase",
							"--spring.cloud.function.routing-expression=headers.func_name",
							"--spring.cloud.function.expected-content-type=text/plain",
							"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("uppercase")
				.metadata("{\"func_name\":\"uppercaseMessage\"}", MimeTypeUtils.APPLICATION_JSON)
				.data("hello")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("HELLO")
				.expectComplete()
				.verify();

		}
	}

	@EnableAutoConfiguration
	@Configuration
	public static class SampleFunctionConfiguration {

		final Sinks.One<byte[]> consumerData = Sinks.one();

		@Bean
		public Function<String, String> uppercase() {
			return v -> v.toUpperCase();
		}

		@Bean
		public Function<Message<String>, String> uppercaseMessage() {
			return msg -> {
				assertThat(msg.getHeaders()
						.get(DestinationPatternsMessageCondition.LOOKUP_DESTINATION_HEADER)).toString().equals("uppercase");
				assertThat(msg.getHeaders()
						.get(FunctionRSocketMessageHandler.RECONCILED_LOOKUP_DESTINATION_HEADER)).toString().equals(RoutingFunction.FUNCTION_NAME);
				return msg.getPayload().toUpperCase();
			};
		}

		@Bean
		public Function<String, String> concat() {
			return v -> v + v;
		}

		@Bean
		public Function<String, String> echo() {
			return v -> v;
		}

		@Bean
		public Function<Flux<String>, Flux<String>> uppercaseReactive() {
			return flux -> flux.map(v -> {
				System.out.println("Uppercasing: " + v);
				return v.toUpperCase();
			});
		}

		@Bean
		public Consumer<byte[]> log() {
			return this.consumerData::tryEmitValue;
		}

		@Bean
		public Supplier<String> source() {
			return () -> "test data";
		}


	}

}
