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
package org.springframework.cloud.function.web.mvc;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.function.web.RestApplication;
import org.springframework.cloud.function.web.mvc.HeadersToMessageTests.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"spring.main.web-application-type=servlet",
		"spring.cloud.function.web.path=/functions" })
@ContextConfiguration(classes = { RestApplication.class, TestConfiguration.class })
public class HeadersToMessageTests {

	@Autowired
	private TestRestTemplate rest;

	@Test
	public void testBodyAndCustomHeaderFromMessagePropagation() throws Exception {
		ResponseEntity<String> postForEntity = rest.postForEntity(
				new URI("/functions/employee"), "{\"name\":\"Bob\",\"age\":25}",
				String.class);
		assertEquals("{\"name\":\"Bob\",\"age\":25}", postForEntity.getBody());
		assertTrue(postForEntity.getHeaders().containsKey("x-content-type"));
		assertEquals("application/xml",
				postForEntity.getHeaders().get("x-content-type").get(0));
		assertEquals("bar", postForEntity.getHeaders().get("foo").get(0));
	}

	@Test
	public void testHeadersPropagatedByDefault() throws Exception {
		HttpEntity<String> postForEntity = rest.exchange(RequestEntity
				.post(new URI("/functions/vanilla")).header("x-context-type", "rubbish")
				.body("{\"name\":\"Bob\",\"age\":25}"), String.class);
		assertEquals("{\"name\":\"Bob\",\"age\":25,\"foo\":\"bar\"}",
				postForEntity.getBody());
		assertTrue(postForEntity.getHeaders().containsKey("x-context-type"));
		assertEquals("rubbish", postForEntity.getHeaders().get("x-context-type").get(0));
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
