/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.function.context.converter.avro;

import java.util.Random;
import java.util.UUID;
import java.util.function.Function;

import com.example.Sensor;
import org.junit.jupiter.api.Test;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Soby Chacko
 */
public class AvroSchemaMessageConverterTests {

	@Test
	public void testAvroSchemaMessageConverter() {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
			SampleFunctionConfiguration.class).web(WebApplicationType.NONE).run(
			"--spring.main.lazy-initialization=true")) {

			FunctionCatalog functionCatalog = context.getBean(FunctionCatalog.class);
			SimpleFunctionRegistry.FunctionInvocationWrapper function = functionCatalog.lookup("avroSensor");

			Random random = new Random();
			Sensor sensor = new Sensor();
			sensor.setId(UUID.randomUUID().toString() + "-v1");
			sensor.setAcceleration(random.nextFloat() * 10);
			sensor.setVelocity(random.nextFloat() * 100);
			sensor.setTemperature(random.nextFloat() * 50);

			final AvroSchemaMessageConverter bean = context.getBean(AvroSchemaMessageConverter.class);
			// Explicitly convert the Sensor to byte[]
			final Message<?> message = bean.toMessage(sensor, new MessageHeaders(null));
			// Now send with the sensor->byte[] converted payload.
			final Sensor enrichedSensor = (Sensor) function.apply(message);

			assertThat(enrichedSensor.getTemperature()).isEqualTo(sensor.getTemperature() + 5);
		}
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class SampleFunctionConfiguration {

		@Bean
		public Function<Sensor, Sensor> avroSensor() {
			return s -> {
				s.setTemperature(s.getTemperature() + 5);
				return s;
			};
		}
	}

}
