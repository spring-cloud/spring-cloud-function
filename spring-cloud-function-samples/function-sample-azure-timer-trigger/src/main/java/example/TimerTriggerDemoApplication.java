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

import java.util.function.Consumer;

import com.microsoft.azure.functions.ExecutionContext;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;

@SpringBootApplication
public class TimerTriggerDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(TimerTriggerDemoApplication.class, args);
	}

	@Bean
	public Consumer<Message<String>> uppercase() {
		return message -> {
			// /timeInfo is a JSON string, you can deserialize it to an object using your favorite JSON library
			String timeInfo = message.getPayload();

			// Business logic -> convert the timeInfo to uppercase.
			String value = timeInfo.toUpperCase();
			
			// (Optionally) access and use the Azure function context.
			ExecutionContext context = (ExecutionContext) message.getHeaders().get("executionContext");
			context.getLogger().info("Timer is triggered with TimeInfo: " + value);

			// No response.
		};
	}
}
