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

package org.springframework.cloud.function.web.mvc;

import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
/**
 *
 * @author Oleg Zhurakousky
 */
public class GeneralIntegrationTests {

	@Test
	public void testMappedAndUnmappedDeleteFunction() throws Exception {
		ApplicationContext context = SpringApplication.run(MultipleConsumerConfiguration.class, "--server.port=0",
				"--spring.cloud.function.http.DELETE=consumer2;supplier;function|consumer1");
		String port = context.getEnvironment().getProperty("local.server.port");
		TestRestTemplate template = new TestRestTemplate();

		ResponseEntity<Void> result = template.exchange(
				RequestEntity.delete(new URI("http://localhost:" + port + "/consumer1"))
				.build(), Void.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

		result = template.exchange(
				RequestEntity.delete(new URI("http://localhost:" + port + "/consumer2"))
				.build(), Void.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		result = template.exchange(
				RequestEntity.delete(new URI("http://localhost:" + port + "/function,consumer1"))
				.build(), Void.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		result = template.exchange(
				RequestEntity.delete(new URI("http://localhost:" + port + "/supplier"))
				.build(), Void.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Test
	public void testMappedAndUnmappedPostPutFunction() throws Exception {
		ApplicationContext context = SpringApplication.run(MultipleConsumerConfiguration.class, "--server.port=0",
				"--spring.cloud.function.http.POST=consumer2;function;supplier;function|consumer1");
		String port = context.getEnvironment().getProperty("local.server.port");
		TestRestTemplate template = new TestRestTemplate();

		ResponseEntity<String> result = template.exchange(RequestEntity
				.post(new URI("http://localhost:" + port + "/consumer1")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);

		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

		result = template.exchange(RequestEntity
				.post(new URI("http://localhost:" + port + "/consumer2")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(result.getBody()).isNull();

		result = template.exchange(RequestEntity
				.post(new URI("http://localhost:" + port + "/function,consumer1")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(result.getBody()).isNull();

		result = template.exchange(RequestEntity
				.post(new URI("http://localhost:" + port + "/function")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("[\"foo\",\"bar\"]");

		result = template.exchange(RequestEntity
				.post(new URI("http://localhost:" + port + "/supplier")).contentType(MediaType.APPLICATION_JSON)
				.body("[\"foo\",\"bar\"]"), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
	}


	@EnableAutoConfiguration
	protected static class MultipleConsumerConfiguration {

		@Bean
		public Consumer<String> consumer1() {
			return v -> { };
		}

		@Bean
		public Consumer<String> consumer2() {
			return v -> { };
		}

		@Bean
		public Function<String, String> function() {
			return v -> v;
		}

		@Bean
		public Supplier<String> supplier() {
			return () -> "";
		}
	}
}
