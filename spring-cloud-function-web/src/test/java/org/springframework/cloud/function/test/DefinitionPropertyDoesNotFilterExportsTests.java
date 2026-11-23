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

package org.springframework.cloud.function.test;

import java.util.function.Function;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Documents that {@code spring.cloud.function.definition} does not restrict which
 * functions are exported by the web module. Restricting is done with
 * {@code spring.cloud.function.http.<METHOD>}.
 */
@SpringBootTest({ "spring.main.web-application-type=REACTIVE",
		"spring.functional.enabled=false",
		"spring.cloud.function.definition=uppercase;reverse" })
@AutoConfigureWebTestClient
@DirtiesContext
public class DefinitionPropertyDoesNotFilterExportsTests {

	@Autowired
	private WebTestClient client;

	@Test
	public void functionNotListedInDefinitionIsStillExported() {
		this.client.post().uri("/lowercase").body(Mono.just("ABC"), String.class).exchange()
				.expectStatus().isOk().expectBody(String.class).isEqualTo("abc");
	}

	@Test
	public void listedFunctionIsExported() {
		this.client.post().uri("/uppercase").body(Mono.just("abc"), String.class).exchange()
				.expectStatus().isOk().expectBody(String.class).isEqualTo("ABC");
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class TestConfiguration {

		@Bean
		public Function<String, String> uppercase() {
			return String::toUpperCase;
		}

		@Bean
		public Function<String, String> reverse() {
			return v -> new StringBuilder(v).reverse().toString();
		}

		@Bean
		public Function<String, String> lowercase() {
			return String::toLowerCase;
		}

	}

}
