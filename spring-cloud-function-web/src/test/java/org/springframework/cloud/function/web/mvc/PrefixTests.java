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
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.function.web.RestApplication;
import org.springframework.cloud.function.web.mvc.PrefixTests.TestConfiguration;
import org.springframework.cloud.function.web.util.HeaderUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"spring.main.web-application-type=servlet",
		"spring.cloud.function.web.path=/functions" })
@ContextConfiguration(classes = { RestApplication.class, TestConfiguration.class })
@AutoConfigureTestRestTemplate
public class PrefixTests {

	@Autowired
	private TestRestTemplate rest;

	@Test
	public void words() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(
				RequestEntity.get(new URI("/functions/words")).build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("[\"foo\",\"bar\"]");
	}

	@Test
	public void missing() throws Exception {
		ResponseEntity<String> result = this.rest
				.exchange(RequestEntity.get(new URI("/words")).build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}


	@Test
	public void uppercase() throws Exception {
		ResponseEntity<String> result = this.rest.exchange(
				RequestEntity.get(new URI("/functions/uppercase/foo?nome=Doe&prenome=John")).build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("[\"foo\",\"bar\"]");
	}

	@EnableAutoConfiguration
	@org.springframework.boot.test.context.TestConfiguration
	protected static class TestConfiguration {

		@Bean({ "words", "get/more" })
		public Supplier<Flux<String>> words() {
			return () -> Flux.fromArray(new String[] { "foo", "bar" });
		}

		@Bean
		public Function<Message<String>, String[]> uppercase() {
			return message -> {
				assertThat(message.getPayload().equals("foo"));
				Map<String, String> httpParam = (Map<String, String>) message.getHeaders().get(HeaderUtils.HTTP_REQUEST_PARAM);
				assertThat(httpParam.get("nome")).isEqualTo("Doe");
				assertThat(httpParam.get("prenome")).isEqualTo("John");
				return new String[] { "foo", "bar" };
			};
		}

	}

}
