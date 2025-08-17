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

import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

@SpringBootApplication
public class EventGridDemoApplication {

	private static final Logger logger = LoggerFactory.getLogger(EventGridDemoApplication.class);

	@Bean
	public Function<CloudEvent, String> processEvent() {
		return event -> {
			logger.info("Received CloudEvent:");
			logger.info("  ID: {}", event.getId());
			logger.info("  Type: {}", event.getType());
			logger.info("  Source: {}", event.getSource());
			logger.info("  Subject: {}", event.getSubject());
			logger.info("  Time: {}", event.getTime());
			
			if (event.getData() != null) {
				logger.info("  Data: {}", new String(event.getData().toBytes(), StandardCharsets.UTF_8));
			}

			// Log additional attributes
			for (String attributeName : event.getAttributeNames()) {
				if (!attributeName.equals("id") && !attributeName.equals("type") 
					&& !attributeName.equals("source") && !attributeName.equals("subject") 
					&& !attributeName.equals("time") && !attributeName.equals("data")) {
					logger.info("  {}: {}", attributeName, event.getAttribute(attributeName));
				}
			}

			// Return a simple acknowledgment
			return String.format("Successfully processed CloudEvent with ID: %s, Type: %s", 
				event.getId(), event.getType());
		};
	}

	@Bean
	public Function<Map<String, Object>, CloudEvent> createEvent() {
		return payload -> {
			logger.info("Creating CloudEvent from payload: {}", payload);
			
			CloudEventBuilder builder = CloudEventBuilder.v1()
				.withId(java.util.UUID.randomUUID().toString())
				.withType("com.example.demo.created")
				.withSource(java.net.URI.create("https://example.com/demo"))
				.withTime(OffsetDateTime.now());

			if (payload.containsKey("subject")) {
				builder.withSubject(payload.get("subject").toString());
			}

			if (payload.containsKey("data")) {
				builder.withData("application/json", payload.get("data").toString().getBytes(StandardCharsets.UTF_8));
			}

			CloudEvent event = builder.build();
			logger.info("Created CloudEvent with ID: {}", event.getId());
			
			return event;
		};
	}

	public static void main(String[] args) {
		SpringApplication.run(EventGridDemoApplication.class, args);
	}

}