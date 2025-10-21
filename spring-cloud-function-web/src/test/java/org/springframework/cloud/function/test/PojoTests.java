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

package org.springframework.cloud.function.test;

import java.util.Locale;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webtestclient.AutoConfigureWebTestClient;
import org.springframework.cloud.function.context.test.FunctionalSpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author Dave Syer
 *
 */
// Only need web-application-type because MVC is on the classpath
@FunctionalSpringBootTest("spring.main.web-application-type=reactive")
@AutoConfigureWebTestClient
public class PojoTests {

	@Autowired
	private WebTestClient client;

	@Test
	public void single() throws Exception {
		this.client.post().uri("/").body(Mono.just("{\"value\":\"foo\"}"), String.class)
				.exchange().expectStatus().isOk().expectBody(String.class)
				.isEqualTo("{\"value\":\"FOO\"}");
	}

	@Test
	public void multiple() throws Exception {
		this.client.post().uri("/")
				.body(Mono.just("[{\"value\":\"foo\"},{\"value\":\"bar\"}]"),
						String.class)
				.exchange().expectStatus().isOk().expectBody(String.class)
				.isEqualTo("[{\"value\":\"FOO\"},{\"value\":\"BAR\"}]");
	}

	@SpringBootConfiguration
	protected static class TestConfiguration implements Function<Foo, Foo> {

		@Override
		public Foo apply(Foo value) {
			return new Foo(value.getValue().toUpperCase(Locale.ROOT));
		}

	}

}

class Foo {

	private String value;

	Foo() {
	}

	Foo(String value) {
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
