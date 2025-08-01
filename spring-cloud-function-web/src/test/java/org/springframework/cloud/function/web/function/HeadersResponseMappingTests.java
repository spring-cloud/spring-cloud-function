/*
 * Copyright 2024-2025 the original author or authors.
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

package org.springframework.cloud.function.web.function;


import java.net.URI;
import java.util.Locale;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.test.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

public class HeadersResponseMappingTests {

	// see https://github.com/spring-cloud/spring-cloud-function/issues/1220
	@Test
	public void test_1220() throws Exception {
		ConfigurableApplicationContext context = SpringApplication.run(ApplicationConfiguration.class,
				"--server.port=0");
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		String port = context.getEnvironment().getProperty("local.server.port");
		ResponseEntity<String> response = testRestTemplate.postForEntity(
				new URI("http://localhost:" + port + "/uppercase"), new Person("John", "Doe"), String.class);
		assertThat(response.getBody()).isEqualTo("JOHN");
	}

	static record Person(String firstName, String lastName) {
	}

	@SpringBootApplication
	protected static class ApplicationConfiguration {

		@Bean
		public Function<Person, String> uppercase() {
			return s -> s.firstName().toUpperCase(Locale.ROOT);
		}

	}
}
