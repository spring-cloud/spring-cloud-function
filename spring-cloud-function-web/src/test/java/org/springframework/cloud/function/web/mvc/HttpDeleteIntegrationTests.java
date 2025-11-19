/*
 * Copyright 2012-present the original author or authors.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.function.web.mvc.HttpDeleteIntegrationTests.ApplicationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Oleg Zhurakousky
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "spring.main.web-application-type=servlet")
@ContextConfiguration(classes = { ApplicationConfiguration.class })
@AutoConfigureTestRestTemplate
public class HttpDeleteIntegrationTests {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private ApplicationConfiguration test;

	@BeforeEach
	public void init() {
		this.test.list.clear();
	}

	@Test
	public void testDeleteConsumer() throws Exception {
		ResponseEntity<Void> result = this.rest.exchange(RequestEntity.delete(new URI("/deleteConsumer/123")).build(),
				Void.class);

		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	public void testDeleteConsumerWithParameters() throws Exception {
		ResponseEntity<Void> result = this.rest
			.exchange(RequestEntity.delete(new URI("/deleteConsumerAsMessage/123?foo=bar")).build(), Void.class);

		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	public void testDeleteWithFunction() throws Exception {
		ResponseEntity<Void> result = this.rest.exchange(RequestEntity.delete(new URI("/deleteFunction")).build(),
				Void.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@EnableAutoConfiguration
	@TestConfiguration
	public static class ApplicationConfiguration {

		private List<String> list = new ArrayList<>();

		public static void main(String[] args) throws Exception {
			SpringApplication.run(HttpDeleteIntegrationTests.ApplicationConfiguration.class, args);
		}

		@Bean
		public Function<String, String> deleteFunction() {
			return v -> {
				assertThat(v).isEqualTo("123");
				System.out.println("Deleting: " + v);
				return null;
			};
		}

		@Bean
		public Consumer<String> deleteConsumer() {
			return v -> {
				assertThat(v).isEqualTo("123");
				System.out.println("Deleting: " + v);
			};
		}

		@Bean
		public Consumer<Message<String>> deleteConsumerAsMessage() {
			return v -> {
				assertThat(v.getPayload()).isEqualTo("123");
				assertThat(((Map) v.getHeaders().get("http_request_param")).get("foo")).isEqualTo("bar");
				System.out.println("Deleting: " + v);
			};
		}

	}

	public static class Foo {

		private String value;

		public Foo(String value) {
			this.value = value;
		}

		Foo() {
		}

		public String getValue() {
			return this.value;
		}

		public void setValue(String value) {
			this.value = value;
		}

	}

}
