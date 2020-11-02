/*
 * Copyright 2012-2019 the original author or authors.
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

import java.time.Duration;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.cloud.function.context.test.FunctionalSpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
@FunctionalSpringBootTest("spring.main.web-application-type=reactive")
@AutoConfigureWebTestClient
public class FunctionalWithInputSetTests {

	@Autowired
	private WebTestClient client;

	@Test
	public void words() throws Exception {
		this.client = this.client.mutate().responseTimeout(Duration.ofSeconds(300)).build();
		String reply = this.client
				.post().uri("/")
				.body(Mono.just("[{\"value\":\"foo\"}, {\"value\":\"bar\"}]"), String.class)
				.exchange()
				.expectStatus().isOk().expectBody(String.class).returnResult()
				.getResponseBody();
		assertThat(reply.contains("FOO")).isTrue();
		assertThat(reply.contains("BAR")).isTrue();
		assertThat(reply.contains("{\"value\":\"")).isTrue();
	}

	@SpringBootConfiguration
	protected static class TestConfiguration implements Function<Set<Foo>, Foo> {

		@Override
		public Foo apply(Set<Foo> value) {
			return new Foo(value.stream().map(foo -> foo.getValue().toUpperCase())
					.collect(Collectors.joining()));
		}

	}

	public static class Foo {

		private String value;

		public Foo() {
		}

		public Foo(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

		public void setValue(String value) {
			this.value = value;
		}

		@Override
		public String toString() {
			return "Foo [value=" + this.value + "]";
		}

	}

}
