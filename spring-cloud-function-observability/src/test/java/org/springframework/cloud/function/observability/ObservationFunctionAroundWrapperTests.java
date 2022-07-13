/*
 * Copyright 2022-2022 the original author or authors.
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

package org.springframework.cloud.function.observability;

import java.util.function.Function;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class ObservationFunctionAroundWrapperTests {

	@Test
	public void testSingleObservation() {
		try (ConfigurableApplicationContext context = SpringApplication.run(SampleConfiguration.class, "")) {
			FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
			Function<Message<String>, Message<byte[]>> uppercase = catalog.lookup("uppercase", "application/json");
			Message<byte[]> result = uppercase.apply(MessageBuilder.withPayload("\"marcin\"").build());
			System.out.println("Result: " + result);

			TestObservationRegistry registry = context.getBean(TestObservationRegistry.class);
			TestObservationRegistryAssert.then(registry).hasSingleObservationThat()
					.hasNameEqualTo("spring.cloud.function");
		}
	}

	@Configuration
	@EnableAutoConfiguration
	public static class SampleConfiguration {

		@Bean
		public ObservationRegistry testRegistry() {
			return TestObservationRegistry.create();
		}

//		@Bean
//		public ObservationFunctionAroundWrapper wrapper(ObservationRegistry registry) {
//			return new ObservationFunctionAroundWrapper(registry);
//		}

		@Bean
		public Function<String, String> uppercase() {
			return v -> v.toUpperCase();
		}
	}
}
