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
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.function.web.RestApplication;
import org.springframework.cloud.function.web.flux.HeaderPropagationTests.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies which request headers are copied into the response (reactive). See the
 * "Http Headers propagation" section of the web documentation.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"spring.main.web-application-type=reactive",
		"spring.cloud.function.http.ignored-headers=x-skip" })
@ContextConfiguration(classes = { RestApplication.class, TestConfiguration.class })
@AutoConfigureTestRestTemplate
public class HeaderPropagationTests {

	@Autowired
	private TestRestTemplate rest;

	private ResponseEntity<String> post() throws Exception {
		return this.rest.exchange(RequestEntity.post(new URI("/echo"))
				.contentType(MediaType.TEXT_PLAIN)
				.header(HttpHeaders.AUTHORIZATION, "Basic c2VjcmV0")
				.header(HttpHeaders.COOKIE, "session=abc")
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.header("X-Custom", "custom-value")
				.header("X-Api-Key", "key-value")
				.header("X-Skip", "skip-value")
				.body("hello"), String.class);
	}

	@Test
	public void sensitiveHeadersAreNotCopiedIntoResponse() throws Exception {
		HttpHeaders headers = post().getHeaders();

		assertThat(headers.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
		assertThat(headers.containsHeader(HttpHeaders.COOKIE)).isFalse();
		assertThat(headers.containsHeader(HttpHeaders.CACHE_CONTROL)).isFalse();
	}

	@Test
	public void otherRequestHeadersAreCopiedIntoResponse() throws Exception {
		HttpHeaders headers = post().getHeaders();

		assertThat(headers.getFirst("x-custom")).isEqualTo("custom-value");
		// not in the built-in list, so it is echoed unless added to ignored-headers
		assertThat(headers.getFirst("x-api-key")).isEqualTo("key-value");
	}

	@Test
	public void configuredIgnoredHeadersAreNotCopiedIntoResponse() throws Exception {
		HttpHeaders headers = post().getHeaders();

		assertThat(headers.containsHeader("x-skip")).isFalse();
	}

	@EnableAutoConfiguration
	@org.springframework.boot.test.context.TestConfiguration
	protected static class TestConfiguration {

		@Bean
		public Function<String, String> echo() {
			return v -> v;
		}

	}

}
