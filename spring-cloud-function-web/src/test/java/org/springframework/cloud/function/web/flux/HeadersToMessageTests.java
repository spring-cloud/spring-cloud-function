/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.function.web.RestApplication;
import org.springframework.cloud.function.web.flux.HeadersToMessageTests.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Dave Syer
 * @author Oleg Zhurakousky
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"spring.cloud.function.web.path=/functions",
		"spring.main.web-application-type=reactive" })
@ContextConfiguration(classes= {RestApplication.class, TestConfiguration.class})
public class HeadersToMessageTests {

	@Autowired
	private TestRestTemplate rest;

	@Test
	public void testBodyAndCustomHeaderFromMessagePropagation() throws Exception {
		// test POJO paylod
		ResponseEntity<String> postForEntity = rest.postForEntity(
				new URI("/functions/employee"), "{\"name\":\"Bob\",\"age\":25}",
				String.class);
		assertEquals("{\"name\":\"Bob\",\"age\":25}", postForEntity.getBody());
		assertTrue(postForEntity.getHeaders().containsKey("x-content-type"));
		assertEquals("application/xml",
				postForEntity.getHeaders().get("x-content-type").get(0));
		assertEquals("bar", postForEntity.getHeaders().get("foo").get(0));

		// test simple type payload
		postForEntity = rest.postForEntity(
				new URI("/functions/string"), "{\"name\":\"Bob\",\"age\":25}",
				String.class);
		assertEquals("{\"name\":\"Bob\",\"age\":25}", postForEntity.getBody());
		assertTrue(postForEntity.getHeaders().containsKey("x-content-type"));
		assertEquals("application/xml",
				postForEntity.getHeaders().get("x-content-type").get(0));
		assertEquals("bar", postForEntity.getHeaders().get("foo").get(0));
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
				Message<Employee> message = MessageBuilder.withPayload(request.getPayload())
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
