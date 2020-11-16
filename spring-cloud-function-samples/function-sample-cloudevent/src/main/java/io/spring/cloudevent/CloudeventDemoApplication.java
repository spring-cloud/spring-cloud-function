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

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.function.cloudevent.CloudEventAttributesProvider;
import org.springframework.cloud.function.cloudevent.CloudEventMessageUtils;
import org.springframework.cloud.function.web.util.HeaderUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.http.RequestEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;

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

	boolean consumerSuccess;

	public static void main(String[] args) throws Exception {
	    SpringApplication.run(CloudeventDemoApplication.class, args);
	}

	@Bean
	public Function<Message<String>, String> asStringMessage() {
		return v -> {
			System.out.println("Received Cloud Event with raw data: " + v);
			return v.getPayload();
		};
	}


	@Bean
	public Function<String, String> asString() {
		return v -> {
			System.out.println("Received raw Cloud Event data: " + v);
			return v;
		};
	}


	@Bean
	public Function<Message<SpringReleaseEvent>, String> asPOJOMessage() {
		return v -> {
			System.out.println("Received Cloud Event with POJO data: " + v);
			return v.getPayload().toString();
		};
	}


	@Bean
	public Function<SpringReleaseEvent, String> asPOJO() {
		return v -> {
			System.out.println("Received POJO Cloud Event data: " + v);
			return v.toString();
		};
	}

	@Bean
	public Function<Message<SpringReleaseEvent>, Message<SpringReleaseEvent>> consumeAndProduceCloudEvent() {
		return ceMessage -> {
			SpringReleaseEvent data = ceMessage.getPayload();
			data.setVersion("2.0");
			data.setReleaseDateAsString("01-10-2006");

			return MessageBuilder.withPayload(data).build();
		};
	}

	@Bean
	public CloudEventAttributesProvider cloudEventAttributesProvider() {
		return attributes -> {
			attributes.setSource("https://interface21.com/").setType("com.interface21");
		};
	}


	@Bean
	public Function<Map<String, Object>, Map<String, Object>> consumeAndProduceCloudEventAsMapToMap() {
		return ceMessage -> {
			ceMessage.put("version", "10.0");
			ceMessage.put("releaseDate", "01-10-2050");
			return ceMessage;
		};
	}

	@Bean
	public Function<SpringReleaseEvent, SpringReleaseEvent> consumeAndProduceCloudEventAsPojoToPojo() {
		return event -> {
			event.setVersion("2.0");
			return event;
		};
	}

	@Bean
	public Consumer<Message<SpringReleaseEvent>> pojoConsumer(CloudEventAttributesProvider provider, RestTemplateBuilder builder) {
			return eventMessage -> {
				RequestEntity<SpringReleaseEvent> entity = RequestEntity.post(URI.create("http://foo.com"))
						.headers(HeaderUtils.fromMessage(
								new MessageHeaders(CloudEventMessageUtils.generateAttributes(eventMessage, provider))))
						.body(eventMessage.getPayload());
				List<String> sourceHeader = entity.getHeaders().get("ce-source");
				Assert.isTrue(sourceHeader.get(0).equals("https://interface21.com/"), "'source' must be https://interface21.com/");
				List<String> typeHeader = entity.getHeaders().get("ce-type");
				Assert.isTrue(typeHeader.get(0).equals("com.interface21"), "'source' must be com.interface21");
				List<String> idHeader = entity.getHeaders().get("ce-id");
				Assert.notEmpty(idHeader, "'id' must not be null");
				List<String> specversionHeader = entity.getHeaders().get("ce-specversion");
				Assert.notEmpty(specversionHeader, "'specversion' must not be null");
				this.consumerSuccess = true;
			};
	}

}
