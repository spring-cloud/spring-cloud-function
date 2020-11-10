/*
 * Copyright 2020-2020 the original author or authors.
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

package io.spring.cloudevent;

import java.util.function.Function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;

/**
 * Sample application that demonstrates how user functions can be triggered by cloud event.
 * Events can come from anywhere (e.g., HTTP, Messaging, RSocket etc).
 * Given that this particular sample comes already with spring-cloud-function-web support each
 * function is a valid REST endpoint where function name signifies URL path (e.g., http://localhost:8080/asPOJOMessage).
 *
 * Simply start the application and post cloud event to individual function - (see individual 'curl' command at each function).
 *
 * You can also run CloudeventDemoApplicationTests.
 *
 * @author Oleg Zhurakousky
 *
 */
@SpringBootApplication
public class CloudeventDemoApplication {

	public static void main(String[] args) throws Exception {
	    SpringApplication.run(CloudeventDemoApplication.class, args);
	}

	/*
	 * curl -w'\n' localhost:8080/asStringMessage \
 	 * -H "Ce-Specversion: 1.0" \
 	 * -H "Ce-Type: com.example.springevent" \
 	 * -H "Ce-Source: spring.io/spring-event" \
 	 * -H "Content-Type: application/json" \
 	 * -H "Ce-Id: 0001" \
 	 * -d '{"releaseDate":"2004-03-24", "releaseName":"Spring Framework", "version":"1.0"}'
	 */
	@Bean
	public Function<Message<String>, String> asStringMessage() {
		return v -> v.getPayload().toString();
	}

	/*
	 * curl -w'\n' localhost:8080/asString \
 	 * -H "Ce-Specversion: 1.0" \
 	 * -H "Ce-Type: com.example.springevent" \
 	 * -H "Ce-Source: spring.io/spring-event" \
 	 * -H "Content-Type: application/json" \
 	 * -H "Ce-Id: 0001" \
 	 * -d '{"releaseDate":"2004-03-24", "releaseName":"Spring Framework", "version":"1.0"}'
	 */
	@Bean
	public Function<String, String> asString() {
		return v -> v;
	}

	/*
	 * curl -w'\n' localhost:8080/asPOJOMessage \
 	 * -H "Ce-Specversion: 1.0" \
 	 * -H "Ce-Type: com.example.springevent" \
 	 * -H "Ce-Source: spring.io/spring-event" \
 	 * -H "Content-Type: application/json" \
 	 * -H "Ce-Id: 0001" \
 	 * -d '{"releaseDate":"2004-03-24", "releaseName":"Spring Framework", "version":"1.0"}'
	 */
	@Bean
	public Function<Message<SpringReleaseEvent>, String> asPOJOMessage() {
		return v -> v.getPayload().toString();
	}

	/*
	 * curl -w'\n' localhost:8080/asPOJO \
 	 * -H "Ce-Specversion: 1.0" \
 	 * -H "Ce-Type: com.example.springevent" \
 	 * -H "Ce-Source: spring.io/spring-event" \
 	 * -H "Content-Type: application/json" \
 	 * -H "Ce-Id: 0001" \
 	 * -d '{"releaseDate":"2004-03-24", "releaseName":"Spring Framework", "version":"1.0"}'
	 */
	@Bean
	public Function<SpringReleaseEvent, String> asPOJO() {
		return v -> v.toString();
	}
}
