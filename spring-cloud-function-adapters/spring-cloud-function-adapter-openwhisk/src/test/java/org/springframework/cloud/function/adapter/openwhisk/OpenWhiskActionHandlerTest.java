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

package org.springframework.cloud.function.adapter.openwhisk;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Kamesh Sampath
 */
@SpringBootTest
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
		this.actionHandler.init(new OpenWhiskInitRequest());
		OpenWhiskActionRequest actionRequest = new OpenWhiskActionRequest();
		actionRequest.setActionName("test_action");
		actionRequest.setValue(eventData);
		Object result = this.actionHandler.run(actionRequest);
		assertThat(result).isNotNull();
		assertThat(result).isEqualTo(
				"{\"result\":{\"name\":\"Spring\",\"message\":\"Hello, Spring\"}}");
	}

	@Test
	public void testHandlerWithoutPayload() {
		Map<String, String> testData = new HashMap<>();
		testData.put("name", "Spring");
		this.actionHandler.init(new OpenWhiskInitRequest());
		OpenWhiskActionRequest actionRequest = new OpenWhiskActionRequest();
		actionRequest.setActionName("test_action");
		Object result = this.actionHandler.run(actionRequest);
		assertThat(result).isNotNull();
		assertThat(result).isEqualTo("{\"result\":\"No input provided\"}");
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
			return this.message;
		}

		public void setMessage(String message) {
			this.message = String.format(this.GREETINGS_FORMAT,
					this.name != null ? this.name : "nobody");
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

	}

}
