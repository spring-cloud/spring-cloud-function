/*
 * Copyright 2019-2019 the original author or authors.
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

package org.springframework.cloud.function.cloudevent;

import java.lang.reflect.Field;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.BeanFactoryAwareFunctionRegistry;
import org.springframework.cloud.function.context.config.SmartCompositeMessageConverter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class CloudEventTypeConversionTests {
	@Test
	public void testFromMessageBinaryPayloadMatchesType() {
		SmartCompositeMessageConverter messageConverter = this.configure(DummyConfiguration.class);
		CloudEventAttributesProvider ceAttrProvider = new DefaultCloudEventAttributesProvider();
		CloudEventAttributes ceAttributes = ceAttrProvider
				.get(UUID.randomUUID().toString(), "1.0", "https://spring.io/", "org.springframework");
		ceAttributes.setDataContentType("text/plain");
		Message<String> message = MessageBuilder.withPayload("Hello Ricky").copyHeaders(ceAttributes).build();

		String converted = (String) messageConverter.fromMessage(message, String.class);
		assertThat(converted).isEqualTo("Hello Ricky");
	}

	@Test
	public void testFromMessageBinaryPayloadDoesNotMatchType() {
		SmartCompositeMessageConverter messageConverter = this.configure(DummyConfiguration.class);
		CloudEventAttributesProvider ceAttrProvider = new DefaultCloudEventAttributesProvider();
		CloudEventAttributes ceAttributes = ceAttrProvider
				.get(UUID.randomUUID().toString(), "1.0", "https://spring.io/", "org.springframework");
		Message<byte[]> message = MessageBuilder.withPayload("Hello Ricky".getBytes())
				.copyHeaders(ceAttributes)
				.setHeader(MessageHeaders.CONTENT_TYPE,
						MimeTypeUtils.parseMimeType("application/cloudevents+json;charset=utf-8"))
				.build();
		String converted = (String) messageConverter.fromMessage(message, String.class);
		assertThat(converted).isEqualTo("Hello Ricky");
	}

	@Test // JsonMessageConverter does some special things between byte[] and String so
			// this works
	public void testFromMessageBinaryPayloadNoDataContentTypeToString() {
		SmartCompositeMessageConverter messageConverter = this.configure(DummyConfiguration.class);
		CloudEventAttributesProvider ceAttrProvider = new DefaultCloudEventAttributesProvider();
		CloudEventAttributes ceAttributes = ceAttrProvider
				.get(UUID.randomUUID().toString(), "1.0", "https://spring.io/", "org.springframework");
		Message<byte[]> message = MessageBuilder.withPayload("Hello Ricky".getBytes())
				.copyHeaders(ceAttributes)
				.setHeader(MessageHeaders.CONTENT_TYPE,
						MimeTypeUtils.parseMimeType("application/cloudevents+json;charset=utf-8"))
				.build();
		String converted = (String) messageConverter.fromMessage(message, String.class);
		assertThat(converted).isEqualTo("Hello Ricky");
	}

	@Test // Unlike the previous test the type here is POJO so no special treatement
	public void testFromMessageBinaryPayloadNoDataContentTypeToPOJO() {
		SmartCompositeMessageConverter messageConverter = this.configure(DummyConfiguration.class);
		CloudEventAttributesProvider ceAttrProvider = new DefaultCloudEventAttributesProvider();
		CloudEventAttributes ceAttributes = ceAttrProvider.get("https://spring.io/", "org.springframework");
		Message<byte[]> message = MessageBuilder.withPayload("Hello Ricky".getBytes())
				.copyHeaders(ceAttributes)
				.setHeader(MessageHeaders.CONTENT_TYPE,
						MimeTypeUtils.parseMimeType("application/cloudevents+json;charset=utf-8"))
				.build();
		String converted = (String) messageConverter.fromMessage(message, Person.class);
		assertThat(converted).isNull();
	}

	@Test // will fall on default CT which is json
	public void testFromMessageBinaryPayloadNoDataContentTypeToPOJOThatWorks() {
		SmartCompositeMessageConverter messageConverter = this.configure(DummyConfiguration.class);
		CloudEventAttributesProvider ceAttrProvider = new DefaultCloudEventAttributesProvider();
		CloudEventAttributes ceAttributes = ceAttrProvider.get("https://spring.io/", "org.springframework");
		Message<byte[]> message = MessageBuilder.withPayload("{\"name\":\"Ricky\"}".getBytes())
				.copyHeaders(ceAttributes)
				.setHeader(MessageHeaders.CONTENT_TYPE,
						MimeTypeUtils.parseMimeType("application/cloudevents+json;charset=utf-8"))
				.build();
		Person converted = (Person) messageConverter.fromMessage(message, Person.class);
		assertThat(converted.getName()).isEqualTo("Ricky");
	}

	@Test // will fall on default CT which is json
	public void testFromMessageStructured() {
		String cloudEventStructured = "{\n" +
				"	    \"specversion\" : \"1.0\",\n" +
				"	    \"type\" : \"org.springframework\",\n" +
				"	    \"source\" : \"https://spring.io/\",\n" +
				"	    \"id\" : \"A234-1234-1234\",\n" +
				"	    \"datacontenttype\" : \"application/json\",\n" +
				"	    \"data\" : {\n" +
				"	        \"version\" : \"1.0\",\n" +
				"	        \"releaseName\" : \"Spring Framework\",\n" +
				"	        \"releaseDate\" : \"24-03-2004\"\n" +
				"	    }\n" +
				"	}";
		SmartCompositeMessageConverter messageConverter = this.configure(DummyConfiguration.class);
		Message<String> message = MessageBuilder.withPayload(cloudEventStructured)
				.setHeader(MessageHeaders.CONTENT_TYPE, CloudEventMessageUtils.APPLICATION_CLOUDEVENTS_VALUE + "+json")
				.setHeader(CloudEventMessageUtils.CE_DATACONTENTTYPE, MimeTypeUtils.APPLICATION_JSON_VALUE).build();
		SpringReleaseEvent springReleaseEvent = (SpringReleaseEvent) messageConverter.fromMessage(message,
				SpringReleaseEvent.class);
		assertThat(springReleaseEvent.getReleaseName()).isEqualTo("Spring Framework");
		assertThat(springReleaseEvent.getVersion()).isEqualTo("1.0");
	}

	private SmartCompositeMessageConverter configure(Class<?>... configClass) {
		ApplicationContext context = new SpringApplicationBuilder(configClass).run(
				"--logging.level.org.springframework.cloud.function=DEBUG", "--spring.main.lazy-initialization=true");
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		Field f = ReflectionUtils.findField(BeanFactoryAwareFunctionRegistry.class, "messageConverter");
		f.setAccessible(true);
		try {
			SmartCompositeMessageConverter messageConverter = (SmartCompositeMessageConverter) f.get(catalog);
			return messageConverter;
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@EnableAutoConfiguration
	@Configuration
	public static class DummyConfiguration {
	}

	public static class Person {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
