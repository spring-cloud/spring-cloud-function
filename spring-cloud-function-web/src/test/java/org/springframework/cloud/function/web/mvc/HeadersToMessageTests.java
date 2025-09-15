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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.test.client.TestRestTemplate;
import org.springframework.cloud.function.web.RestApplication;
import org.springframework.cloud.function.web.mvc.HeadersToMessageTests.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Oleg Zhurakousky
 *
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"spring.main.web-application-type=servlet",
		"spring.cloud.function.web.path=/functions" })
@ContextConfiguration(classes = { RestApplication.class, TestConfiguration.class })
public class HeadersToMessageTests {

	@Autowired
	private TestRestTemplate rest;

	@Test
	public void testBodyAndCustomHeaderFromMessagePropagation() throws Exception {
		HttpEntity<Map> postForEntity = this.rest
				.exchange(RequestEntity.post(new URI("/functions/employee"))
						.contentType(MediaType.APPLICATION_JSON)
						.body("{\"name\":\"Bob\",\"age\":25}"), Map.class);
		assertThat(postForEntity.getBody()).containsExactlyInAnyOrderEntriesOf(Map.of("name", "Bob", "age", 25));
		assertThat(postForEntity.getHeaders().containsHeader("x-content-type")).isTrue();
		assertThat(postForEntity.getHeaders().get("x-content-type").get(0))
				.isEqualTo("application/xml");
		assertThat(postForEntity.getHeaders().get("foo").get(0)).isEqualTo("bar");
	}

	@Test
	public void testHeadersPropagatedByDefault() throws Exception {
		HttpEntity<Map> postForEntity = this.rest
				.exchange(RequestEntity.post(new URI("/functions/vanilla"))
						.contentType(MediaType.APPLICATION_JSON)
						.header("x-context-type", "rubbish")
						.body("{\"name\":\"Bob\",\"age\":25}"), Map.class);
		assertThat(postForEntity.getBody()).containsExactlyInAnyOrderEntriesOf(Map.of("name", "Bob", "age", 25, "foo", "bar"));

		assertThat(postForEntity.getHeaders().containsHeader("x-context-type")).isTrue();
		assertThat(postForEntity.getHeaders().get("x-context-type").get(0))
				.isEqualTo("rubbish");
	}

	@EnableAutoConfiguration
	@org.springframework.boot.test.context.TestConfiguration
	protected static class TestConfiguration {

		@Bean({ "employee" })
		public Function<Message<Map<String, Object>>, Message<Map<String, Object>>> function() {
			return request -> {
				Message<Map<String, Object>> message = MessageBuilder
						.withPayload(request.getPayload())
						.setHeader("X-Content-Type", "application/xml")
						.setHeader("foo", "bar").build();
				return message;
			};
		}

		@Bean
		public Function<Map<String, Object>, Map<String, Object>> vanilla() {
			return request -> {
				Map<String, Object> message = new LinkedHashMap<>(request);
				message.put("foo", "bar");
				return message;
			};
		}

	}

}
