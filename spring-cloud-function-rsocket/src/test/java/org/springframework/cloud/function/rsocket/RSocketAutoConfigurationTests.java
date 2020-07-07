/*
 * Copyright 2020-2020 the original author or authors.
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

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
public class RSocketAutoConfigurationTests {

	@Test
	public void testRequestReplyFunction() throws Exception {
		new SpringApplicationBuilder(SampleFunctionConfiguration.class).run(
				"--logging.level.org.springframework.cloud.function=DEBUG",
				"--spring.cloud.function.definition=uppercase",
				"--spring.cloud.function.rsocket.bind-address=localhost",
				"--spring.cloud.function.rsocket.bind-port=12345");

		RSocket socket = RSocketConnector.connectWith(TcpClientTransport.create("localhost", 12345)).log()
				.retryWhen(Retry.backoff(5, Duration.ofSeconds(1))).block();
		socket.requestResponse(DefaultPayload.create("\"hello\"")).map(Payload::getDataUtf8).subscribe(System.out::println);

		Thread.sleep(1000);
//		assertThat(result).isEqualTo("\"HELLO\"");
	}

	@Test
	public void testRequestChannelFunction() throws Exception {
		new SpringApplicationBuilder(SampleFunctionConfiguration.class).run(
				"--logging.level.org.springframework.cloud.function=DEBUG",
				"--spring.cloud.function.definition=uppercaseReactive",
				"--spring.cloud.function.rsocket.bind-address=localhost",
				"--spring.cloud.function.rsocket.bind-port=12345");

		RSocket socket = RSocketConnector.connectWith(TcpClientTransport.create("localhost", 12345)).log()
				.retryWhen(Retry.backoff(5, Duration.ofSeconds(1))).block();
		socket.requestChannel(Flux.just(DefaultPayload.create("\"Ricky\""), DefaultPayload.create("\"Julien\""), DefaultPayload.create("\"Bubbles\"")))
			.subscribe(System.out::println);

		Thread.sleep(1000);
//		assertThat(result).isEqualTo("\"HELLO\"");
	}

	@Test
	public void testRequestReplyFunctionWithComposition() throws Exception {
		new SpringApplicationBuilder(SampleFunctionConfiguration.class).web(WebApplicationType.NONE).run(
				"--logging.level.org.springframework.cloud.function=DEBUG",
				"--spring.cloud.function.definition=uppercase",
				"--spring.cloud.function.rsocket.bind-address=localhost",
				"--spring.cloud.function.rsocket.bind-port=12345");

		new SpringApplicationBuilder(AdditionalFunctionConfiguration.class).web(WebApplicationType.NONE).run(
				"--logging.level.org.springframework.cloud.function=DEBUG",
				"--spring.cloud.function.definition=reverse", "--spring.cloud.function.rsocket.bind-address=localhost",
				"--spring.cloud.function.rsocket.bind-port=12346",
				"--spring.cloud.function.rsocket.target-address=localhost",
				"--spring.cloud.function.rsocket.target-port=12345");

		RSocket socket = RSocketConnector.connectWith(TcpClientTransport.create("localhost", 12346)).log()
				.retryWhen(Retry.backoff(5, Duration.ofSeconds(1))).block();
		socket.requestResponse(DefaultPayload.create("\"hello\"")).map(Payload::getDataUtf8).subscribe(System.out::println);
		Thread.sleep(1000);
//		assertThat(result).isEqualTo("\"OLLEH\"");
	}

//	@Test
//	public void testFireAndForgetConsumer() throws Exception {
//		new SpringApplicationBuilder(SampleFunctionConfiguration.class)
//				.run("--logging.level.org.springframework.cloud.function=DEBUG",
//					 "--spring.cloud.function.definition=log");
//
//		RSocket socket = RSocketConnector.connectWith(TcpClientTransport.create("localhost", 7000))
//				.log()
//				.retryWhen(Retry.backoff(5, Duration.ofSeconds(1)))
//				.block();
//		socket.fireAndForget(DefaultPayload.create("Hello"))
//			.log()
//			.onErrorContinue((e, x) -> {
//				System.out.println(e);
//			})
//			.block();
//		Thread.sleep(2000);
//		System.out.println();
//	}

	@EnableAutoConfiguration
	@Configuration
	public static class SampleFunctionConfiguration {
		@Bean
		public Function<String, String> uppercase() {
			return v -> v.toUpperCase();
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
			return v -> {
				System.out.println("==> In Consumer: " + new String(v));
			};
		}
	}

	@EnableAutoConfiguration
	@Configuration
	public static class AdditionalFunctionConfiguration {
		@Bean
		public Function<String, String> reverse() {
			return v -> new StringBuilder(v).reverse().toString();
		}
	}
}
