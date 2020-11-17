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

package org.springframework.cloud.function.cloudevent;

import java.text.SimpleDateFormat;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class CloudEventFunctionTests {

	@SuppressWarnings("unchecked")
	@Test
	public void testBinaryPojoToPojoDefaultOutputAttributeProvider() {
		Function<Object, Object> function = this.lookup("echo", TestConfiguration.class);

		Message<String> inputMessage = MessageBuilder.withPayload("{\"name\":\"Ricky\"}")
				.copyHeaders(CloudEventMessageUtils.get("https://spring.io/", "org.springframework")).build();
		assertThat(CloudEventMessageUtils.isBinary(inputMessage.getHeaders())).isTrue();

		Message<Person> resultMessage = (Message<Person>) function.apply(inputMessage);

		/*
		 * Validates that although user only deals with POJO, the framework recognizes
		 * both on input and output that it is dealing with Cloud Event and generates
		 * appropriate headers/attributes
		 */
		CloudEventAttributes attributes = new CloudEventAttributes(resultMessage.getHeaders());
		assertThat(attributes.isValidCloudEvent()).isTrue();
		assertThat((String) attributes.getSource()).isEqualTo(Person.class.getName());
		assertThat((String) attributes.getType()).isEqualTo("http://spring.io/application-application");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testStructuredPojoToPojoDefaultOutputAttributeProvider() throws Exception {
		String payload = "{\n" +
				"    \"specversion\" : \"1.0\",\n" +
				"    \"type\" : \"org.springframework\",\n" +
				"    \"source\" : \"https://spring.io/\",\n" +
				"    \"id\" : \"A234-1234-1234\",\n" +
				"    \"datacontenttype\" : \"application/json\",\n" +
				"    \"data\" : {\n" +
				"        \"version\" : \"1.0\",\n" +
				"        \"releaseName\" : \"Spring Framework\",\n" +
				"        \"releaseDate\" : \"24-03-2004\"\n" +
				"    }\n" +
				"}";
		Function<Object, Object> function = this.lookup("springRelease", TestConfiguration.class);

		Message<String> inputMessage = MessageBuilder.withPayload(payload)
				.setHeader(MessageHeaders.CONTENT_TYPE, CloudEventMessageUtils.APPLICATION_CLOUDEVENTS_VALUE + "+json")
				.build();
		assertThat(CloudEventMessageUtils.isBinary(inputMessage.getHeaders())).isFalse();

		Message<SpringReleaseEvent> resultMessage = (Message<SpringReleaseEvent>) function.apply(inputMessage);
		assertThat(resultMessage.getPayload().getReleaseDate())
				.isEqualTo(new SimpleDateFormat("dd-MM-yyyy").parse("01-10-2006"));
		assertThat(resultMessage.getPayload().getVersion()).isEqualTo("2.0");
		/*
		 * Validates that although user only deals with POJO, the framework recognizes
		 * both on input and output that it is dealing with Cloud Event and generates
		 * appropriate headers/attributes
		 */
		CloudEventAttributes attributes = new CloudEventAttributes(resultMessage.getHeaders());
		assertThat(attributes.isValidCloudEvent()).isTrue();
		assertThat((String) attributes.getSource()).isEqualTo(SpringReleaseEvent.class.getName());
		assertThat((String) attributes.getType()).isEqualTo("http://spring.io/application-application");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testStructuredPojoToPojoDefaultOutputAttributeProviderNoDataContentType() throws Exception {
		String payload = "{\n" +
				"    \"ce_specversion\" : \"1.0\",\n" +
				"    \"ce_type\" : \"org.springframework\",\n" +
				"    \"ce_source\" : \"https://spring.io/\",\n" +
				"    \"ce_id\" : \"A234-1234-1234\",\n" +
				"    \"ce_data\" : {\n" +
				"        \"version\" : \"1.0\",\n" +
				"        \"releaseName\" : \"Spring Framework\",\n" +
				"        \"releaseDate\" : \"24-03-2004\"\n" +
				"    }\n" +
				"}";
		Function<Object, Object> function = this.lookup("springRelease", TestConfiguration.class);

		Message<String> inputMessage = MessageBuilder.withPayload(payload)
				.setHeader(MessageHeaders.CONTENT_TYPE, CloudEventMessageUtils.APPLICATION_CLOUDEVENTS_VALUE + "+json")
				.build();
		assertThat(CloudEventMessageUtils.isBinary(inputMessage.getHeaders())).isFalse();

		Message<SpringReleaseEvent> resultMessage = (Message<SpringReleaseEvent>) function.apply(inputMessage);
		assertThat(resultMessage.getPayload().getReleaseDate())
				.isEqualTo(new SimpleDateFormat("dd-MM-yyyy").parse("01-10-2006"));
		assertThat(resultMessage.getPayload().getVersion()).isEqualTo("2.0");
		/*
		 * Validates that although user only deals with POJO, the framework recognizes
		 * both on input and output that it is dealing with Cloud Event and generates
		 * appropriate headers/attributes
		 */
		CloudEventAttributes attributes = new CloudEventAttributes(resultMessage.getHeaders());
		assertThat(attributes.isValidCloudEvent()).isTrue();
		assertThat((String) attributes.getSource()).isEqualTo(SpringReleaseEvent.class.getName());
		assertThat((String) attributes.getType()).isEqualTo("http://spring.io/application-application");
	}

	private Function<Object, Object> lookup(String functionDefinition, Class<?>... configClass) {
		ApplicationContext context = new SpringApplicationBuilder(configClass).run(
				"--logging.level.org.springframework.cloud.function=DEBUG", "--spring.main.lazy-initialization=true");
		return context.getBean(FunctionCatalog.class).lookup(functionDefinition);
	}

	@EnableAutoConfiguration
	@Configuration
	public static class TestConfiguration {
		@Bean
		Function<Person, Person> echo() {
			return Function.identity();
		}

		@Bean
		Function<SpringReleaseEvent, SpringReleaseEvent> springRelease() {
			return event -> {
				try {
					event.setReleaseDate(new SimpleDateFormat("dd-MM-yyyy").parse("01-10-2006"));
					event.setVersion("2.0");
					return event;
				}
				catch (Exception e) {
					throw new IllegalArgumentException(e);
				}
			};
		}
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
