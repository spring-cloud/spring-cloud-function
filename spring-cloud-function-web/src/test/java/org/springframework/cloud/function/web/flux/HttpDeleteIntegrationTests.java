/*
 * Copyright 2026-present the original author or authors.
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.function.web.RestApplication;
import org.springframework.cloud.function.web.flux.HttpDeleteIntegrationTests.ApplicationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies HTTP DELETE handling of consumers on WebFlux. The argument derived from the path is
 * deliberately not asserted here, only a request body is.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"spring.main.web-application-type=reactive",
		"spring.cloud.function.http.PUT=other" })
@ContextConfiguration(classes = { RestApplication.class, ApplicationConfiguration.class })
@AutoConfigureTestRestTemplate
public class HttpDeleteIntegrationTests {

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private ApplicationConfiguration test;

	@BeforeEach
	public void init() {
		this.test.deleted.clear();
	}

	@Test
	public void deleteInvokesConsumer() throws Exception {
		ResponseEntity<Void> result = this.rest.exchange(
				RequestEntity.delete(new URI("/deleteConsumer/123")).build(), Void.class);

		assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(this.test.deleted).hasSize(1);
	}

	@Test
	public void deleteWithRequestBodyPassesBodyToConsumer() throws Exception {
		ResponseEntity<Void> result = this.rest.exchange(
				RequestEntity.method(HttpMethod.DELETE, new URI("/deleteConsumer")).body("456"), Void.class);

		assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(this.test.deleted).containsExactly("456");
	}

	@Test
	public void methodNotListedInExplicitMappingsIsNotAllowed() throws Exception {
		ResponseEntity<Void> result = this.rest.exchange(
				RequestEntity.put(new URI("/deleteConsumer")).body("x"), Void.class);

		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
	}

	@EnableAutoConfiguration
	@org.springframework.boot.test.context.TestConfiguration
	protected static class ApplicationConfiguration {

		private final List<String> deleted = new ArrayList<>();

		@Bean
		public Consumer<String> deleteConsumer() {
			return this.deleted::add;
		}

	}

}
