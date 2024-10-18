/*
 * Copyright 2022 the original author or authors.
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
package example;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import example.entity.KafkaEntity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;

@SpringBootApplication
public class KafkaTriggerDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(KafkaTriggerDemoApplication.class, args);
	}

	@Bean
	public Function<Message<String>, String> uppercase(JsonMapper mapper) {
		return message -> {

			// Convert the message payload into Azure's KafkaEntity format.
			KafkaEntity kafkaEntity = mapper.fromJson(message.getPayload(), KafkaEntity.class);

			// Business logic: convert the JSON string values into uppercase.
			if (kafkaEntity.getValue() != null) {
				Map<String, Object> valueMap = mapper.fromJson(kafkaEntity.getValue(), Map.class);
				if (valueMap != null) {
					valueMap.forEach((k, v) -> valueMap.put(k,
							v != null && v instanceof String ? ((String) v).toUpperCase(Locale.ROOT) : null));
					return mapper.toString(valueMap);
				}
			}

			return mapper.toString(null);
		};
	}
}
