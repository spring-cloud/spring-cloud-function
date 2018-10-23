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

import java.util.function.Function;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.cloud.function.context.test.FunctionalSpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
@RunWith(SpringRunner.class)
// Only need web-application-type because MVC is on the classpath
@FunctionalSpringBootTest("spring.main.web-application-type=reactive")
@AutoConfigureWebTestClient
public class HeadersToMessageTests {

	@Autowired
	private WebTestClient client;

	@Test
	public void testBodyAndCustomHeaderFromMessagePropagation() throws Exception {
		client.post().uri("/").body(Mono.just("foo"), String.class).exchange()
				.expectStatus().isOk().expectHeader()
				.valueEquals("x-content-type", "application/xml").expectHeader()
				.valueEquals("foo", "bar").expectBody(String.class).isEqualTo("FOO");
	}

	@SpringBootConfiguration
	protected static class TestConfiguration
			implements Function<Message<String>, Message<String>> {
		public Message<String> apply(Message<String> request) {
			Message<String> message = MessageBuilder.withPayload(request.getPayload().toUpperCase())
					.setHeader("X-Content-Type", "application/xml")
					.setHeader("foo", "bar").build();
			return message;
		}
	}
}
