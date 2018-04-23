/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.function.adapter.openwhisk;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Kamesh Sampath
 */
@RunWith(SpringRunner.class)
@SpringBootTest()
@EnableAutoConfiguration
@TestPropertySource(locations = "classpath:/application-test.properties")
public class OpenWhiskActionHandlerTest {

	@Autowired
	OpenWhiskActionHandler actionHandler;

	@Autowired
	ObjectMapper mapper;

	@Test
	public void testHandlerWithPayload() {
		Map<String, String> testData = new HashMap<>();
		testData.put("name", "Spring");
		Map<String, Object> eventData = new HashMap<>();
		eventData.put("payload", testData);
		actionHandler.init(new OpenWhiskInitRequest());
		OpenWhiskActionRequest actionRequest = new OpenWhiskActionRequest();
		actionRequest.setActionName("test_action");
		actionRequest.setValue(eventData);
		Object result = actionHandler.run(actionRequest);
		assertNotNull(result);
		assertEquals("{\"result\":{\"name\":\"Spring\",\"message\":\"Hello, Spring\"}}",
				result);
	}

	@Test
	public void testHandlerWithoutPayload() {
		Map<String, String> testData = new HashMap<>();
		testData.put("name", "Spring");
		actionHandler.init(new OpenWhiskInitRequest());
		OpenWhiskActionRequest actionRequest = new OpenWhiskActionRequest();
		actionRequest.setActionName("test_action");
		Object result = actionHandler.run(actionRequest);
		assertNotNull(result);
		assertEquals("{\"result\":\"No input provided\"}", result);
	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class,
			JacksonAutoConfiguration.class })
	protected static class OWFunctionConfig {

		@Bean
		public Function<Greetings, Greetings> greeter() {
			return v -> new OpenWhiskActionHandlerTest.Greetings(v.getName());
		}

		@Bean
		@Scope("prototype")
		public OpenWhiskActionHandler actionHandler() {
			return new OpenWhiskActionHandler();
		}

		@Bean
		public FunctionProperties properties() {
			return new FunctionProperties();
		}

	}

	protected static class Greetings {

		private final String GREETINGS_FORMAT = "Hello, %s";

		private String name;
		private String message;

		public Greetings() {
		}

		public Greetings(String name) {
			this.name = name;
			setMessage("");
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = String.format(GREETINGS_FORMAT,
					this.name != null ? name : "nobody");
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

}
