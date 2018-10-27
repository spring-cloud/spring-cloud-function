/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.test;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.cloud.function.context.test.FunctionalSpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;

/**
 * @author Dave Syer
 *
 */
@RunWith(SpringRunner.class)
@FunctionalSpringBootTest("spring.main.web-application-type=reactive")
@AutoConfigureWebTestClient
public class FunctionalWithInputListTests {

	@Autowired
	private WebTestClient client;

	@Test
	public void words() throws Exception {
		client.post().uri("/").body(Mono.just("[{\"value\":\"foo\"}, {\"value\":\"bar\"}]"), String.class)
				.exchange().expectStatus().isOk().expectBody(String.class)
				.isEqualTo("{\"value\":\"FOOBAR\"}");
	}

	@SpringBootConfiguration
	protected static class TestConfiguration implements Function<List<Foo>, Foo> {
		@Override
		public Foo apply(List<Foo> value) {
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
