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

import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.messaging.Message;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class MessagingTests {

	@Test
	public void testPojoToStringViaMessage() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(MessagingConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			Person p = new Person();
			p.setName("Ricky");
			Message<Person> message = MessageBuilder.withPayload(p).setHeader("someHeader", "foo").build();

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("pojoToString")
				.data(message)
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("RICKY")
				.expectComplete()
				.verify();
		}
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testPojoToStringViaMessageMap() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(MessagingConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			Person p = new Person();
			p.setName("Ricky");
			Message<Person> message = MessageBuilder.withPayload(p).setHeader("someHeader", "foo").build();

			JsonMapper jsonMapper = applicationContext.getBean(JsonMapper.class);
			Map map = jsonMapper.fromJson(message, Map.class);

			rsocketRequesterBuilder.tcp("localhost", port)
				.route("pojoToString")
				.data(map)
				.retrieveMono(String.class)
				.as(StepVerifier::create)
				.expectNext("RICKY")
				.expectComplete()
				.verify();
		}
	}

	@Test
	public void testPojoToStringViaMessageExpectMessage() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(MessagingConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			Person p = new Person();
			p.setName("Ricky");
			Message<Person> message = MessageBuilder.withPayload(p).setHeader("someHeader", "foo").build();

			Message<String> result = rsocketRequesterBuilder.tcp("localhost", port)
				.route("pojoToString")
				.data(message)
				.retrieveMono(new ParameterizedTypeReference<Message<String>>() {
				})
				.block();

			assertThat(result.getPayload()).isEqualTo("RICKY");
			assertThat(result.getHeaders().get("someHeader")).isEqualTo("foo");
		}
	}

	@Test
	public void testPojoMessageToPojoViaMessage() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(MessagingConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			Person p = new Person();
			p.setName("Ricky");
			Message<Person> message = MessageBuilder.withPayload(p).setHeader("someHeader", "foo").build();

			Person result = new Person();
			result.setName(p.getName().toUpperCase());
			rsocketRequesterBuilder.tcp("localhost", port)
				.route("pojoMessageToPojo")
				.data(message)
				.retrieveMono(Person.class)
				.as(StepVerifier::create)
				.expectNext(result)
				.expectComplete()
				.verify();
		}
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testPojoMessageToPojoViaMap() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(MessagingConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			Person p = new Person();
			p.setName("Ricky");
			Message<Person> message = MessageBuilder.withPayload(p).setHeader("someHeader", "foo").build();

			JsonMapper jsonMapper = applicationContext.getBean(JsonMapper.class);
			Map map = jsonMapper.fromJson(message, Map.class);

			Person result = new Person();
			result.setName(p.getName().toUpperCase());
			rsocketRequesterBuilder.tcp("localhost", port)
				.route("pojoMessageToPojo")
				.data(map)
				.retrieveMono(Person.class)
				.as(StepVerifier::create)
				.expectNext(result)
				.expectComplete()
				.verify();
		}
	}

	@Test
	public void testPojoMessageToPojoViaMessageExpectMessage() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(MessagingConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			Person p = new Person();
			p.setName("Ricky");
			Message<Person> message = MessageBuilder.withPayload(p).setHeader("someHeader", "foo").build();

			Message<Person> result = rsocketRequesterBuilder.tcp("localhost", port)
				.route("pojoMessageToPojo")
				.data(message)
				.retrieveMono(new ParameterizedTypeReference<Message<Person>>() {
				})
				.block();

			assertThat(result.getPayload().getName()).isEqualTo("RICKY");
			assertThat(result.getHeaders().get("someHeader")).isEqualTo("foo");
		}
	}

	@Test
	public void testPojoMessageToPojoViaMessageExpectMessageRawPayload() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(MessagingConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			Message<byte[]> message = MessageBuilder.withPayload("{\"name\":\"bob\"}".getBytes())
					.setHeader("someHeader", "foo")
					.build();

			Message<byte[]> result = rsocketRequesterBuilder.tcp("localhost", port)
				.route("pojoMessageToPojo")
				.data(message)
				.retrieveMono(new ParameterizedTypeReference<Message<byte[]>>() {
				})
				.block();

			assertThat(result.getPayload()).isEqualTo("{\"name\":\"BOB\"}".getBytes());
			assertThat(result.getHeaders().get("someHeader")).isEqualTo("foo");
		}
	}

	@Test
	public void testPojoMessageToPojoViaMessageExpectMessageStringPayload() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(MessagingConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);

			Message<String> message = MessageBuilder.withPayload("{\"name\":\"bob\"}")
					.setHeader("someHeader", "foo")
					.build();

			Message<String> result = rsocketRequesterBuilder.tcp("localhost", port)
				.route("pojoMessageToPojo")
				.data(message)
				.retrieveMono(new ParameterizedTypeReference<Message<String>>() {
				})
				.block();

			assertThat(result.getPayload()).isEqualTo("{\"name\":\"BOB\"}");
			assertThat(result.getHeaders().get("someHeader")).isEqualTo("foo");
		}
	}

	@Test
	public void testPojoToMessageMap() {
		int port = SocketUtils.findAvailableTcpPort();
		try (
			ConfigurableApplicationContext applicationContext =
				new SpringApplicationBuilder(MessagingConfiguration.class)
					.web(WebApplicationType.NONE)
					.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.rsocket.server.port=" + port);
		) {
			RSocketRequester.Builder rsocketRequesterBuilder =
				applicationContext.getBean(RSocketRequester.Builder.class);
			Person p = new Person();
			p.setName("Ricky");

			Message<Map<String, Object>> result = rsocketRequesterBuilder.tcp("localhost", port)
				.route("echoMessageMap")
				.data(p)
				.retrieveMono(new ParameterizedTypeReference<Message<Map<String, Object>>>() {
				})
				.block();

			assertThat(((Map) result.getPayload()).get("name")).isEqualTo("Ricky");
		}
	}



	@EnableAutoConfiguration
	@Configuration
	public static class MessagingConfiguration {

		@Bean
		public Function<Person, String> pojoToString() {
			return v -> {
				return v.getName().toUpperCase();
			};
		}

		@Bean
		public Function<Message<Map<String, Object>>, Message<Map<String, Object>>> echoMessageMap() {
			return v -> {
				assertThat(v.getHeaders()).containsKey("rsocketFrameType");
				assertThat(v.getHeaders()).containsKey("lookupDestination");
				return v;
			};
		}

		@Bean
		public Function<Flux<Message<Map<String, Object>>>, Flux<Message<Map<String, Object>>>> echoMessageMapReactive() {
			return v -> {
				return v;
			};
		}

		@Bean
		public Function<Message<Person>, Person> pojoMessageToPojo() {
			return p -> {
				assertThat(p.getHeaders().get("someHeader").equals("foo"));
				Person newPerson = new Person();
				newPerson.setName(p.getPayload().getName().toUpperCase());
				return newPerson;
			};
		}

		@Bean
		public Function<Message<Person>, Message<Person>> pojoMessageToPojoMessage() {
			return p -> {
				assertThat(p.getHeaders().get("someHeader").equals("foo"));
				Person newPerson = new Person();
				newPerson.setName(p.getPayload().getName().toUpperCase());
				return MessageBuilder.withPayload(newPerson).copyHeaders(p.getHeaders()).setHeader("xyz", "hello").build();
			};
		}

	}

	public static class Person {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return this.name;
		}

		@Override
		public int hashCode() {
			return super.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Person && (this.name.equals(((Person) obj).name));
		}
	}
}
