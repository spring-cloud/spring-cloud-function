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

package example;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.microsoft.azure.functions.ExecutionContext;

@SpringBootApplication
public class Config {

	public static void main(String[] args) throws Exception {
		SpringApplication.run(Config.class, args);
	}

	@Bean
	public Function<String, String> uppercase(ExecutionContext context) {
		return value -> {
			ObjectMapper mapper = new ObjectMapper();

			try {
				Map<String, String> map = mapper.readValue(value, Map.class);

				if(map != null)
					map.forEach((k, v) -> map.put(k, v != null ? v.toUpperCase() : null));

				if(context != null)
					context.getLogger().info(new StringBuilder().append("Function: ").append(context.getFunctionName()).append(" is uppercasing ").append(value.toString()).toString());

				return mapper.writeValueAsString(map);
			} catch (IOException e) {
				if(context != null)
					context.getLogger().severe("Function could not parse incoming request");

				return ("Function error: - bad request");
			}
		};
	}

}

