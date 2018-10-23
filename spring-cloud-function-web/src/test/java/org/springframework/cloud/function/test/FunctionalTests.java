/*
 * Copyright 2012-2015 the original author or authors.
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

import java.util.function.Function;

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
//Only need web-application-type because MVC is on the classpath
@FunctionalSpringBootTest("spring.main.web-application-type=reactive")
@AutoConfigureWebTestClient
public class FunctionalTests {

	@Autowired
	private WebTestClient client;

	@Test
	public void words() throws Exception {
		client.post().uri("/").body(Mono.just("foo"), String.class).exchange()
				.expectStatus().isOk().expectBody(String.class).isEqualTo("FOO");
	}

	@SpringBootConfiguration
	protected static class TestConfiguration implements Function<String, String> {
		@Override
		public String apply(String value) {
			return value.toUpperCase();
		}
	}
}
