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

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.cloudevent.CloudEventMessageUtils;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class CloudeventDemoApplicationFunctionTests {

	@Test
	public void demoPureFunctionInvocation() {

		try(ConfigurableApplicationContext context = new SpringApplicationBuilder(CloudeventDemoApplication.class)
				.web(WebApplicationType.NONE).run()) {
			FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
			Message<String> binaryCloudEventMessage = MessageBuilder
					.withPayload("{\"releaseDate\":\"24-03-2004\", \"releaseName\":\"Spring Framework\", \"version\":\"1.0\"}")
					.copyHeaders(CloudEventMessageUtils.get("spring.io/spring-event", "com.example.springevent"))
					.build();

			/*
			 * NOTE how it makes no difference what the actual function signature
			 * is (see `asPOJOMessage` and `asPOJO` specifically). Type conversion will happen
			 * inside spring-cloud-function.
			 */
			Function<Message<String>, Message<String>> asPojoMessage = catalog.lookup("asPOJOMessage");
			System.out.println(asPojoMessage.apply(binaryCloudEventMessage));

			Function<Message<String>, Message<String>> asPojo = catalog.lookup("asPOJO");
			System.out.println(asPojo.apply(binaryCloudEventMessage));

			Function<Message<String>, Message<String>> asString = catalog.lookup("asString");
			System.out.println(asString.apply(binaryCloudEventMessage));

			Function<Message<String>, Message<String>> asStringMessage = catalog.lookup("asStringMessage");
			System.out.println(asStringMessage.apply(binaryCloudEventMessage));
		}
	}

	@Test
	public void demoPureFunctionProduceConsumeCloudEvent() {
		try(ConfigurableApplicationContext context = new SpringApplicationBuilder(CloudeventDemoApplication.class)
				.web(WebApplicationType.NONE).run()) {
			FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
			Message<String> binaryCloudEventMessage = MessageBuilder
					.withPayload("{\"releaseDate\":\"24-03-2004\", \"releaseName\":\"Spring Framework\", \"version\":\"1.0\"}")
					.copyHeaders(CloudEventMessageUtils.get("spring.io/spring-event", "com.example.springevent"))
					.build();

			/*
			 * NOTE how it makes no difference what the actual function signature
			 * is (see `asPOJOMessage` and `asPOJO` specifically). Type conversion will happen
			 * inside spring-cloud-function.
			 */
			Function<Message<String>, Message<String>> asPojoMessage = catalog.lookup("consumeAndProduceCloudEvent");
			System.out.println(asPojoMessage.apply(binaryCloudEventMessage));
		}
	}

	@Test
	public void demoPureFunctionProduceConsumeCloudEventAsPojo() {
		try(ConfigurableApplicationContext context = new SpringApplicationBuilder(CloudeventDemoApplication.class)
				.web(WebApplicationType.NONE).run()) {
			FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
			Message<String> binaryCloudEventMessage = MessageBuilder
					.withPayload("{\"releaseDate\":\"24-03-2004\", \"releaseName\":\"Spring Framework\", \"version\":\"1.0\"}")
					.copyHeaders(CloudEventMessageUtils.get("spring.io/spring-event", "com.example.springevent"))
					.build();

			/*
			 * NOTE how it makes no difference what the actual function signature
			 * is (see `asPOJOMessage` and `asPOJO` specifically). Type conversion will happen
			 * inside spring-cloud-function.
			 */
			Function<Message<String>, Message<String>> asPojoMessage = catalog.lookup("consumeAndProduceCloudEventAsPojoToPojo");
			System.out.println(asPojoMessage.apply(binaryCloudEventMessage));
			//ce_source=https://interface21.com/
//			ce_type=com.interface21
			//ce_type=com.interface21, ce_source=https://interface21.com/
			//{ce_type=http://spring.io/application-application, ce_source=io.spring.cloudevent.SpringReleaseEvent, ce_specversion=1.0, ce_id=eba0eda2-ab01-4f62-b369-6eb473106c4a, id=e31a2670-6000-4954-1b90-a864d2ac6fc6, timestamp=1605627860374}
			//{ce_type=io.spring.cloudevent.SpringReleaseEvent, ce_source=http://spring.io/application-application, ce_specversion=1.0, ce_id=93980c85-c478-471f-8a00-69bf288fe22b, id=2ded0773-3a78-8e73-feb5-7274f5439d64, timestamp=1605627907645}
		}
	}
}
