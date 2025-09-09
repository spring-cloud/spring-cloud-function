/*
 * Copyright 2021-present the original author or authors.
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

import java.util.Locale;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.MessageRoutingCallback;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;

/**
 *
 * @author Oleg Zhurakousky
 * @author Chris Bono
 */
public class MessageRoutingCallbackRSocketTests {

	@Test
	public void testRoutingWithRoutingCallback() {
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(RoutingCallbackFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
							"--spring.cloud.function.expected-content-type=text/plain",
							"--spring.rsocket.server.port=0");
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			int port = applicationContext.getEnvironment().getProperty("local.rsocket.server.port", Integer.class);

			// imperative
			rsocketRequesterBuilder.tcp("localhost", port)
				.route("foo")
				.metadata("{\"func_name\":\"uppercase\"}", MimeTypeUtils.APPLICATION_JSON)
				.data("hello")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("HELLO")
				.expectComplete()
				.verify();

			// imperative Message
			rsocketRequesterBuilder.tcp("localhost", port)
				.route("foo")
				.metadata("{\"func_name\":\"uppercaseMessage\"}", MimeTypeUtils.APPLICATION_JSON)
				.data("hello")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("HELLO")
				.expectComplete()
				.verify();

			// reactive
			rsocketRequesterBuilder.tcp("localhost", port)
				.route("foo")
				.metadata("{\"func_name\":\"uppercaseReactive\"}", MimeTypeUtils.APPLICATION_JSON)
				.data("hello")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("HELLO")
				.expectComplete()
				.verify();

			// reactive
			rsocketRequesterBuilder.tcp("localhost", port)
				.route("foo")
				.metadata("{\"func_name\":\"uppercaseReactiveMessage\"}", MimeTypeUtils.APPLICATION_JSON)
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
	public static class RoutingCallbackFunctionConfiguration {
		@Bean
		public MessageRoutingCallback customRouter() {
			return new MessageRoutingCallback() {
				@Override
				public String routingResult(Message<?> message) {
					return (String) message.getHeaders().get("func_name");
				}
			};
		}

		@Bean
		public Function<String, String> uppercase() {
			return v -> v.toUpperCase(Locale.ROOT);
		}

		@Bean
		public Function<Message<String>, Message<String>> uppercaseMessage() {
			return m  -> MessageBuilder.withPayload(m.getPayload().toUpperCase(Locale.ROOT)).copyHeaders(m.getHeaders()).build();
		}

		@Bean
		public Function<Flux<String>, Flux<String>> uppercaseReactive() {
			return flux -> flux.map(v -> v.toUpperCase(Locale.ROOT));
		}

		@Bean
		public Function<Flux<Message<String>>, Flux<Message<String>>> uppercaseReactiveMessage() {
			return flux -> flux.map(m  -> MessageBuilder.withPayload(m.getPayload().toUpperCase(Locale.ROOT)).copyHeaders(m.getHeaders()).build());
		}

		@Bean
		public Function<String, String> concat() {
			return v -> v + v;
		}
	}
}
