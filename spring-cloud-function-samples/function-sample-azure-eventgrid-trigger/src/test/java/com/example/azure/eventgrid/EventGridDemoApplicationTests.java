/*
 * Copyright 2024-2024 the original author or authors.
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

package com.example.azure.eventgrid;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class EventGridDemoApplicationTests {

	@Test
	void testProcessEventFunctionLogic() {
		// Test the core function logic without Spring context
		EventGridDemoApplication app = new EventGridDemoApplication();
		Function<CloudEvent, String> processEvent = app.processEvent();
		
		CloudEvent testEvent = CloudEventBuilder.v1()
			.withId("test-id-123")
			.withType("com.example.test.created")
			.withSource(URI.create("https://example.com/test"))
			.withSubject("test-subject")
			.withTime(OffsetDateTime.now())
			.withData("application/json", "{\"message\":\"test data\"}".getBytes())
			.build();

		String result = processEvent.apply(testEvent);
		
		assertThat(result).isNotNull();
		assertThat(result).contains("test-id-123");
		assertThat(result).contains("com.example.test.created");
	}

	@Test
	void testCreateEventFunctionLogic() {
		// Test the core function logic without Spring context
		EventGridDemoApplication app = new EventGridDemoApplication();
		Function<Map<String, Object>, CloudEvent> createEvent = app.createEvent();
		
		Map<String, Object> payload = Map.of(
			"subject", "test-subject",
			"data", Map.of("message", "test data", "value", 42)
		);

		CloudEvent result = createEvent.apply(payload);
		
		assertThat(result).isNotNull();
		assertThat(result.getId()).isNotNull();
		assertThat(result.getType()).isEqualTo("com.example.demo.created");
		assertThat(result.getSource()).isEqualTo(URI.create("https://example.com/demo"));
		assertThat(result.getSubject()).isEqualTo("test-subject");
		assertThat(result.getData()).isNotNull();
	}
}