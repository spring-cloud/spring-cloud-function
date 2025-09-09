/*
 * Copyright 2019-present the original author or authors.
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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 * @author Chris Bono
 * @since 2.1
 */
public class UserSubmittedTests {

	@Test
	public void testIssue274() throws Exception {
		ConfigurableApplicationContext context = SpringApplication.run(Issue274Configuration.class, "--server.port=0");
		int port = context.getEnvironment().getProperty("local.server.port", Integer.class);
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		Thread.sleep(200);
		ResponseEntity<String> response = testRestTemplate
				.postForEntity(new URI("http://localhost:" + port + "/echo"), "", String.class);
		assertThat(response.getBody()).isNull();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	public void testIssue274WithData() throws Exception {
		ConfigurableApplicationContext context = SpringApplication.run(Issue274Configuration.class, "--server.port=0");
		int port = context.getEnvironment().getProperty("local.server.port", Integer.class);
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		Thread.sleep(200);
		ResponseEntity<String> response = testRestTemplate
				.postForEntity(new URI("http://localhost:" + port + "/echo"), "hello", String.class);
		assertThat(response.getBody()).isEqualTo("HELLO");
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}


	@SpringBootApplication
	protected static class Issue274Configuration {

		@Bean
		public Function<String, String> echo() {
			return s -> s.toUpperCase(Locale.ROOT);
		}
	}

}
