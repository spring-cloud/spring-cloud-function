/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.cloud.function.web.mvc;

import java.net.URI;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.cloud.function.web.RestApplication;
import org.springframework.cloud.function.web.mvc.RoutingFunctionTests.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Oleg Zhurakousky
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"spring.main.web-application-type=servlet",
		"spring.cloud.function.web.path=/functions",
		"spring.cloud.function.routing.enabled=true"})
@ContextConfiguration(classes = { RestApplication.class, TestConfiguration.class })
@Ignore
public class RoutingFunctionTests {

	@Autowired
	private TestRestTemplate rest;

	@Test
	public void testFunctionMessage() throws Exception {
		HttpEntity<String> postForEntity = this.rest
				.exchange(RequestEntity.post(new URI("/functions/" + RoutingFunction.FUNCTION_NAME))
						.contentType(MediaType.APPLICATION_JSON)
						.header("function.name", "employee")
						.body("{\"name\":\"Bob\",\"age\":25}"), String.class);
		assertThat(postForEntity.getBody()).isEqualTo("{\"name\":\"Bob\",\"age\":25}");
		assertThat(postForEntity.getHeaders().containsKey("x-content-type")).isTrue();
		assertThat(postForEntity.getHeaders().get("x-content-type").get(0))
				.isEqualTo("application/xml");
		assertThat(postForEntity.getHeaders().get("foo").get(0)).isEqualTo("bar");
	}

	@Test
	public void testFunctionPrimitive() throws Exception {
		ResponseEntity<String> postForEntity = this.rest
				.exchange(RequestEntity.post(new URI("/functions/" + RoutingFunction.FUNCTION_NAME))
						.contentType(MediaType.TEXT_PLAIN)
						.header("function.name", "echo")
						.body("{\"name\":\"Bob\",\"age\":25}"), String.class);
		assertThat(postForEntity.getBody()).isEqualTo("{\"name\":\"Bob\",\"age\":25}");
		assertThat(postForEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	public void testFluxFunctionPrimitive() throws Exception {
		ResponseEntity<String> postForEntity = this.rest
				.exchange(RequestEntity.post(new URI("/functions/" + RoutingFunction.FUNCTION_NAME))
						.contentType(MediaType.TEXT_PLAIN)
						.header("function.name", "fluxuppercase")
						.body("hello"), String.class);
		assertThat(postForEntity.getBody()).isEqualTo("[\"HELLO\"]");
		assertThat(postForEntity.getStatusCode()).isEqualTo(HttpStatus.OK);

		postForEntity = this.rest
		.exchange(RequestEntity.post(new URI("/functions/" + RoutingFunction.FUNCTION_NAME))
				.contentType(MediaType.TEXT_PLAIN)
				.header("function.name", "fluxuppercase")
				.body("hello1"), String.class);
		assertThat(postForEntity.getBody()).isEqualTo("[\"HELLO1\"]");
		assertThat(postForEntity.getStatusCode()).isEqualTo(HttpStatus.OK);

		postForEntity = this.rest
		.exchange(RequestEntity.post(new URI("/functions/" + RoutingFunction.FUNCTION_NAME))
				.contentType(MediaType.TEXT_PLAIN)
				.header("function.name", "fluxuppercase")
				.body("hello2"), String.class);
		assertThat(postForEntity.getBody()).isEqualTo("[\"HELLO2\"]");
		assertThat(postForEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	public void testFluxFunctionPrimitiveArray() throws Exception {
		ResponseEntity<String> postForEntity = this.rest
				.exchange(RequestEntity.post(new URI("/functions/" + RoutingFunction.FUNCTION_NAME))
						.contentType(MediaType.APPLICATION_JSON)
						.header("function.name", "fluxuppercase")
						.body(new String[] {"a", "b", "c"}), String.class);
		assertThat(postForEntity.getBody()).isEqualTo("[\"A\",\"B\",\"C\"]");
		assertThat(postForEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	public void testFluxConsumer() throws Exception {
		ResponseEntity<String> postForEntity = this.rest
				.exchange(RequestEntity.post(new URI("/functions/" + RoutingFunction.FUNCTION_NAME))
						.contentType(MediaType.APPLICATION_JSON)
						.header("function.name", "fluxconsumer")
						.body(new String[] {"a", "b", "c"}), String.class);
		assertThat(postForEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
	}


	@Test
	public void testFunctionPojo() throws Exception {
		ResponseEntity<String> postForEntity = this.rest
				.exchange(RequestEntity.post(new URI("/functions/" + RoutingFunction.FUNCTION_NAME))
						.contentType(MediaType.APPLICATION_JSON)
						.header("function.name", "echoPojo")
						.body("{\"value\":\"foo\"}"), String.class);
		assertThat(postForEntity.getBody()).isEqualTo("{\"foo\":{\"value\":\"foo\"},\"value\":\"bar\"}");
		assertThat(postForEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	public void testConsumerMessage() throws Exception {
		ResponseEntity<String> postForEntity = this.rest
				.exchange(RequestEntity.post(new URI("/functions/" + RoutingFunction.FUNCTION_NAME))
						.contentType(MediaType.TEXT_PLAIN)
						.header("function.name", "messageConsumer")
						.body("{\"name\":\"Bob\",\"age\":25}"), String.class);
		assertThat(postForEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@EnableAutoConfiguration
	@org.springframework.boot.test.context.TestConfiguration
	protected static class TestConfiguration {

		@Bean({ "employee" })
		public Function<Message<Map<String, Object>>, Message<Map<String, Object>>> function() {
			return request -> {
				Message<Map<String, Object>> message = MessageBuilder
						.withPayload(request.getPayload())
						.setHeader("X-Content-Type", "application/xml")
						.setHeader("foo", "bar").build();
				return message;
			};
		}

		@Bean
		public Consumer<Message<String>> messageConsumer() {
			return value -> System.out.println("Value: " + value);
		}

		@Bean
		public Function<String, String> echo() {
			return v -> v;
		}

		@Bean
		public Function<Flux<String>, Flux<String>> fluxuppercase() {
			return v -> v.map(s -> {
				System.out.println(s);
				return s.toUpperCase();
			});
		}

		@Bean
		public Consumer<Flux<String>> fluxconsumer() {
			return flux -> flux.doOnNext(s -> {
				System.out.println("Received: " + s);
			}).subscribe();
		}

		@Bean
		public Function<Foo, Bar> echoPojo() {
			return v -> {
				Bar bar = new Bar();
				bar.setFoo(v);
				bar.setValue("bar");
				return bar;
			};
		}

	}

	public static class Foo {
		private String value;

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

	public static class Bar {
		private Foo foo;
		private String value;
		public Foo getFoo() {
			return foo;
		}
		public void setFoo(Foo foo) {
			this.foo = foo;
		}
		public String getValue() {
			return value;
		}
		public void setValue(String value) {
			this.value = value;
		}


	}

}
