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

package org.springframework.cloud.function.web.mvc;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.function.web.RestApplication;
import org.springframework.cloud.function.web.mvc.RoutingAllowListTests.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that when the HTTP method allow-list does not list {@code functionRouter},
 * the router can not be used to reach functions through routing headers.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"spring.main.web-application-type=servlet",
		"spring.cloud.function.http.post=uppercase" })
@ContextConfiguration(classes = { RestApplication.class, TestConfiguration.class })
@AutoConfigureTestRestTemplate
public class RoutingAllowListTests {

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private TestConfiguration config;

	@BeforeEach
	public void init() {
		this.config.invoked.clear();
	}

	@Test
	public void listedFunctionIsInvokedDirectly() throws Exception {
		ResponseEntity<String> result = post("/uppercase", null);

		assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(this.config.invoked).containsExactly("uppercase");
	}

	@Test
	public void unlistedFunctionIsRejected() throws Exception {
		ResponseEntity<String> result = post("/secret", null);

		assertThat(result.getStatusCode().is2xxSuccessful()).isFalse();
		assertThat(this.config.invoked).isEmpty();
	}

	@Test
	public void unlistedRouterIsRejectedEvenWhenHeaderNamesListedFunction() throws Exception {
		ResponseEntity<String> result = post("/functionRouter", "uppercase");

		assertThat(result.getStatusCode().is2xxSuccessful()).isFalse();
		assertThat(this.config.invoked).isEmpty();
	}

	@Test
	public void unlistedRouterIsRejectedWhenHeaderNamesUnlistedFunction() throws Exception {
		ResponseEntity<String> result = post("/functionRouter", "secret");

		assertThat(result.getStatusCode().is2xxSuccessful()).isFalse();
		assertThat(this.config.invoked).isEmpty();
	}

	private ResponseEntity<String> post(String path, String routingDefinition) throws Exception {
		RequestEntity.BodyBuilder request = RequestEntity.post(new URI(path)).contentType(MediaType.TEXT_PLAIN);
		if (routingDefinition != null) {
			request.header("spring.cloud.function.definition", routingDefinition);
		}
		return this.rest.exchange(request.body("hello"), String.class);
	}

	@EnableAutoConfiguration
	@org.springframework.boot.test.context.TestConfiguration
	protected static class TestConfiguration {

		private final List<String> invoked = new ArrayList<>();

		@Bean
		public Function<String, String> uppercase() {
			return v -> {
				this.invoked.add("uppercase");
				return v.toUpperCase(Locale.ROOT);
			};
		}

		@Bean
		public Function<String, String> secret() {
			return v -> {
				this.invoked.add("secret");
				return v;
			};
		}

	}

}
