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

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.rsocket.context.RSocketServerBootstrap;
import org.springframework.boot.rsocket.server.RSocketServer;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
public class RSocketAutoConfigurationTests {

	@Test
	public void testNonExistingFunctionInRoute() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("foo")
				.data("\"hello\"")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectError()
				.verify();
		}
	}

	@Test
	public void testNonExistingFunctionInRouteSingleFunctionInCatalog() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SingleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("blah")
				.data("\"hello\"")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("hello")
				.expectComplete()
				.verify();
		}
	}



	@Test
	public void testImperativeFunctionAsRequestReplyWithDefinition() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.cloud.function.definition=uppercase",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("")
				.data("\"hello\"")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("HELLO")
				.expectComplete()
				.verify();
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testWithCborContentType() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.cloud.function.definition=uppercase",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			Person p = new Person();
			p.setAge(23);
			p.setName("Bob");
			Map<String, Object> m = rsocketRequesterBuilder
				.dataMimeType(MimeType.valueOf("application/cbor"))
				.tcp("localhost", port)
				.route("echoMap")
				.data(p)
				.retrieveMono(Map.class).block();
			assertThat(m.get("name")).isEqualTo("Bob");
			assertThat(m.get("age")).isEqualTo(23);
		}
	}

	@Test
	@Disabled
	public void testImperativeFunctionAsRequestReplyWithDefinitionExplicitExpectedOutputCt() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.cloud.function.definition=uppercase",
						"--spring.cloud.function.expected-content-type=application/json",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("")
				.data("\"hello\"")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("\"HELLO\"")
				.expectComplete()
				.verify();
		}
	}

	@Test
	public void testImperativeFunctionAsRequestReply() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("uppercase")
				.data("hello")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("HELLO")
				.expectComplete()
				.verify();
		}
	}

	@Test
	public void testWithRouteAndDefinition() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
							"--spring.cloud.function.definition=echo",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("uppercase")
				.data("hello")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("HELLO")
				.expectComplete()
				.verify();
		}
	}

	@Test
	public void testImperativeFunctionAsRequestReplyWithComposition() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("uppercase|concat")
				.data("\"hello\"")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("HELLOHELLO")
				.expectComplete()
				.verify();
		}
	}

	@Test
	public void testSupplierAsRequestReply() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("source")
				.data("\"hello\"")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("test data")
				.expectComplete()
				.verify();
		}
	}

	@Test
	public void testImperativeFunctionAsRequestStream() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("uppercase")
				.data("\"hello\"")
				.retrieveFlux(String.class)
				.as(StepVerifier::create)
				.expectNext("HELLO")
				.expectComplete()
				.verify();
		}
	}

	@Test
	public void testImperativeFunctionAsRequestChannel() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("uppercase")
				//.data(Flux.just("\"Ricky\"", "\"Julien\"", "\"Bubbles\""))
				.data(Flux.just("Ricky", "Julien", "Bubbles"))
				.retrieveFlux(String.class)
				.as(StepVerifier::create)
				.expectNext("RICKY", "JULIEN", "BUBBLES")
				.expectComplete()
				.verify();
		}
	}

	@Test
	public void testReactiveFunctionAsRequestReply() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("uppercaseReactive")
				.data("hello")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("HELLO")
				.expectComplete()
				.verify();
		}
	}

	@Test
	public void testReactiveFunctionAsRequestStream() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("uppercaseReactive")
				.data("hello")
				.retrieveFlux(String.class)
				.as(StepVerifier::create)
				.expectNext("HELLO")
				.expectComplete()
				.verify();
			rsocketRequesterBuilder.tcp("localhost", port)
				.route("uppercaseReactive")
				.data("hello")
				.retrieveFlux(String.class)
				.as(StepVerifier::create)
				.expectNext("HELLO")
				.expectComplete()
				.verify();
			rsocketRequesterBuilder.tcp("localhost", port)
				.route("uppercaseReactive")
				.data("hello")
				.retrieveFlux(String.class)
				.as(StepVerifier::create)
				.expectNext("HELLO")
				.expectComplete()
				.verify();
		}
	}

	@Test
	public void testReactiveFunctionAsRequestChannel() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("uppercaseReactive")
				.data(Flux.just("\"Ricky\"", "\"Julien\"", "\"Bubbles\""))
				.retrieveFlux(String.class)
				.as(StepVerifier::create)
				.expectNext("RICKY", "JULIEN", "BUBBLES")
				.expectComplete()
				.verify();
		}
	}

	@Test
	public void testRequestReplyFunctionWithDistributedComposition() {
		int portA = SocketUtils.findAvailableTcpPort();
		int portB = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.cloud.function.definition=uppercase|concat",
						"--spring.rsocket.server.port=" + portA);
		) {

			try (
				ConfigurableApplicationContext applicationContext2 =
					new SpringApplicationBuilder(AdditionalFunctionConfiguration.class)
						.web(WebApplicationType.NONE)
						.run("--logging.level.org.springframework.cloud.function=DEBUG",
							"--spring.cloud.function.definition=reverse>localhost:" + portA + "|wrap",
							"--spring.rsocket.server.port=" + portB);
			) {

				RSocketRequester.Builder rsocketRequesterBuilder =
					applicationContext2.getBean(RSocketRequester.Builder.class);

				rsocketRequesterBuilder.tcp("localhost", portB)
					.route("reverse>localhost:" + portA + "|wrap")
					.data("\"hello\"")
					.retrieveMono(String.class)
					.as(StepVerifier::create)
					.expectNext("(OLLEHOLLEH)")
					.expectComplete()
					.verify();
			}
		}
	}

	@Disabled("TODO")
	@Test
	public void testCompositionOverWebSocket() {
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.REACTIVE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.cloud.function.definition=uppercase|concat",
						"--spring.rsocket.server.transport=websocket",
						"--spring.rsocket.server.mapping-path=rsockets",
						"--server.port=0");
		) {
			ConfigurableEnvironment environment = applicationContext.getEnvironment();
			String httpServerPort = environment.getProperty("local.server.port");

			try (
				ConfigurableApplicationContext applicationContext2 =
					new SpringApplicationBuilder(AdditionalFunctionConfiguration.class)
						.web(WebApplicationType.NONE)
						.run("--logging.level.org.springframework.cloud.function=DEBUG",
							"--spring.cloud.function.definition=reverse>http://localhost:" + httpServerPort + "/rsockets/uppercase|wrap",
							"--spring.rsocket.server.port=0");
			) {
				RSocketServerBootstrap serverBootstrap = applicationContext2.getBean(RSocketServerBootstrap.class);
				RSocketServer server = (RSocketServer) ReflectionTestUtils.getField(serverBootstrap, "server");

				RSocketRequester.Builder rsocketRequesterBuilder =
					applicationContext2.getBean(RSocketRequester.Builder.class);

				rsocketRequesterBuilder.tcp("localhost", server.address().getPort())
					.route("reverse")
					.data("\"hello\"")
					.retrieveMono(String.class)
					.as(StepVerifier::create)
					.expectNext("\"(OLLEHOLLEH)\"")
					.expectComplete()
					.verify();
			}
		}
	}

	@Test
	public void testFireAndForgetConsumer() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {

			SampleFunctionConfiguration config = applicationContext.getBean(SampleFunctionConfiguration.class);


			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("log")
				.data("\"hello\"")
				.send()
				.as(StepVerifier::create)
				.expectComplete()
				.verify();
			String result = config.consumerData.asMono().block();
			assertThat(result).isEqualTo("hello");
		}
	}

	@Test
	public void testRsocketRoutesForAllFunctions() {
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(AdditionalFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=0");
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);
			RSocketServerBootstrap serverBootstrap = applicationContext.getBean(RSocketServerBootstrap.class);
			RSocketServer server = (RSocketServer) ReflectionTestUtils.getField(serverBootstrap, "server");

			RSocketRequester requester = rsocketRequesterBuilder.tcp("localhost", server.address().getPort());

			requester.route("reverse")
				.data("hello")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("olleh")
				.expectComplete()
				.verify();

			requester.route("wrap")
				.data("\"hello\"")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("(hello)")
				.expectComplete()
				.verify();
		}
	}

	@Test
	public void testRoutingWithRoutingFunction() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
							"--spring.cloud.function.routing-expression=headers.function_definition",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route(RoutingFunction.FUNCTION_NAME)
				.metadata("{\"function_definition\":\"uppercase|concat\"}", MimeTypeUtils.APPLICATION_JSON)
				.data("hello")
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("HELLOHELLO")
				.expectComplete()
				.verify();
		}
	}

	@Test
	public void testByteArrayInOut() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(SampleFunctionConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			String result = rsocketRequesterBuilder.tcp("localhost", port)
				.route("uppercase")
				.data("hello".getBytes())
				.retrieveMono(String.class)
				.block();

			assertThat(result).isEqualTo("HELLO");

			byte[] resultBytes = rsocketRequesterBuilder.tcp("localhost", port)
					.route("uppercase")
					.data("hello".getBytes())
					.retrieveMono(byte[].class)
					.block();

			assertThat(resultBytes).isEqualTo("HELLO".getBytes());
		}
	}

	@EnableAutoConfiguration
	@Configuration
	public static class SampleFunctionConfiguration {

		final Sinks.One<String> consumerData = Sinks.one();

		@Bean
		public Function<String, String> uppercase() {
			return v -> v.toUpperCase();
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
		public Function<Map<String, Object>, Map<String, Object>> echoMap() {
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
		public Consumer<String> log() {
			return this.consumerData::tryEmitValue;
		}

		@Bean
		public Supplier<String> source() {
			return () -> "test data";
		}

	}

	@EnableAutoConfiguration
	@Configuration
	public static class AdditionalFunctionConfiguration {

		@Bean
		public Function<String, String> reverse() {
			return v -> new StringBuilder(v).reverse().toString();
		}

		@Bean
		public Function<String, String> wrap() {
			return v -> "(" + v + ")";
		}

	}

	@EnableAutoConfiguration
	@Configuration
	public static class SingleFunctionConfiguration {
		@Bean
		public Function<String, String> echo() {
			return v -> v;
		}
	}

	public static class Person {
		private String name;
		private int age;
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public int getAge() {
			return age;
		}
		public void setAge(int age) {
			this.age = age;
		}
	}

}
