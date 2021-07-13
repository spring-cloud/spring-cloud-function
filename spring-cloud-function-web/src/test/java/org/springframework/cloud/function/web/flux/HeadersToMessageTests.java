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

package org.springframework.cloud.function.web.flux;

import java.net.URI;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.function.web.RestApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 * @author Adrien Poupard
 *
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"spring.cloud.function.web.path=/functions",
		"spring.main.web-application-type=reactive" })
@ContextConfiguration(classes = { RestApplication.class, HeadersToMessageTests.TestConfiguration.class })
public class HeadersToMessageTests {

	@Autowired
	private TestRestTemplate rest;

	@Test
	public void testBodyAndCustomHeaderFromMessagePropagation() throws Exception {
		// test POJO paylod
		ResponseEntity<String> postForEntity = this.rest
				.exchange(RequestEntity.post(new URI("/functions/employee"))
						.contentType(MediaType.APPLICATION_JSON)
						.body("{\"name\":\"Bob\",\"age\":25}"), String.class);
		assertThat(postForEntity.getBody()).isEqualTo("{\"name\":\"Bob\",\"age\":25}");
		assertThat(postForEntity.getHeaders().containsKey("x-content-type")).isTrue();
		assertThat(postForEntity.getHeaders().get("x-content-type").get(0))
				.isEqualTo("application/xml");
		assertThat(postForEntity.getHeaders().get("foo").get(0)).isEqualTo("bar");

		// test simple type payload
		postForEntity = this.rest.postForEntity(new URI("/functions/string"),
				"{\"name\":\"Bob\",\"age\":25}", String.class);
		assertThat(postForEntity.getBody()).isEqualTo("{\"name\":\"Bob\",\"age\":25}");
		assertThat(postForEntity.getHeaders().containsKey("x-content-type")).isTrue();
		assertThat(postForEntity.getHeaders().get("x-content-type").get(0))
				.isEqualTo("application/xml");
		assertThat(postForEntity.getHeaders().get("foo").get(0)).isEqualTo("bar");
	}

	@EnableAutoConfiguration
	@org.springframework.boot.test.context.TestConfiguration
	protected static class TestConfiguration {

		@Bean({ "string" })
		public Function<Message<String>, Message<String>> functiono() {
			return request -> {
				Message<String> message = MessageBuilder.withPayload(request.getPayload())
						.setHeader("X-Content-Type", "application/xml")
						.setHeader("foo", "bar").build();
				return message;
			};
		}

		@Bean({ "employee" })
		public Function<Message<Employee>, Message<Employee>> function1() {
			return request -> {
				Message<Employee> message = MessageBuilder
						.withPayload(request.getPayload())
						.setHeader("X-Content-Type", "application/xml")
						.setHeader("foo", "bar").build();
				return message;
			};
		}

	}

	@SuppressWarnings("unused") // used by json converter
	private static class Employee {

		private String name;

		private int age;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public int getAge() {
			return this.age;
		}

		public void setAge(int age) {
			this.age = age;
		}

	}

}
