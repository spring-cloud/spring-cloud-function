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

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.message.MessageUtils;
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
	public void testBinaryPojoToPojoDefaultOutputHeaderProvider() {
		Function<Object, Object> function = this.lookup("echo", TestConfiguration.class);

		String id = UUID.randomUUID().toString();

		Message<String> inputMessage = CloudEventMessageBuilder
			.withData("{\"name\":\"Ricky\"}")
			.setId(id)
			.setSource("https://spring.io/")
			.setType("org.springframework")
			.build();

		assertThat(CloudEventMessageUtils.isCloudEvent(inputMessage)).isTrue();

		Message<Person> resultMessage = (Message<Person>) function.apply(inputMessage);


		/*
		 * Validates that although user only deals with POJO, the framework recognizes
		 * both on input and output that it is dealing with Cloud Event and generates
		 * appropriate headers/attributes
		 */
		assertThat(CloudEventMessageUtils.isCloudEvent(resultMessage)).isTrue();
		assertThat(CloudEventMessageUtils.getType(resultMessage)).isEqualTo(Person.class.getName());
		assertThat(CloudEventMessageUtils.getSource(resultMessage)).isEqualTo(URI.create("http://spring.io/"));
	}

	/*
	 * Aside from the properly processing and recognizing CE, the following tow tests (imperative and reactive)
	 * also emulate message coming from one protocol going to another via MessageUtils.TARGET_PROTOCOL header that
	 * is set here explicitly but for instance in s-c-stream is set by the framework
	 */
	@Test
	public void testBinaryPojoToPojoDefaultOutputHeaderProviderImperative() {
		Function<Object, Object> function = this.lookup("springRelease", TestConfiguration.class);

		String id = UUID.randomUUID().toString();

		String payload = "{\n" +
				"        \"version\" : \"1.0\",\n" +
				"        \"releaseName\" : \"Spring Framework\",\n" +
				"        \"releaseDate\" : \"24-03-2004\"\n" +
				"    }";

		Message<String> inputMessage = CloudEventMessageBuilder
			.withData(payload)
			.setId(id)
			.setSource("https://spring.io/")
			.setType("org.springframework")
			.setHeader(MessageUtils.TARGET_PROTOCOL, CloudEventMessageUtils.Protocols.KAFKA)
			.build(CloudEventMessageUtils.AMQP_ATTR_PREFIX);

		assertThat(CloudEventMessageUtils.isCloudEvent(inputMessage)).isTrue();

		Message<?> message = (Message<?>) function.apply(inputMessage);

		/*
		 * Validates that although user only deals with POJO, the framework recognizes
		 * both on input and output that it is dealing with Cloud Event and generates
		 * appropriate headers/attributes
		 */

		assertThat(CloudEventMessageUtils.isCloudEvent(message)).isTrue();
		assertThat(CloudEventMessageUtils.getType(message)).isEqualTo(SpringReleaseEvent.class.getName());
		assertThat(CloudEventMessageUtils.getSource(message)).isEqualTo(URI.create("http://spring.io/"));
		assertThat(message.getHeaders().get("ce_source")).isEqualTo(URI.create("http://spring.io/"));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testBinaryPojoToPojoDefaultOutputHeaderProviderReactive() {
		Function<Object, Object> function = this.lookup("springReleaseReactive", TestConfiguration.class);

		String id = UUID.randomUUID().toString();

		String payload = "{\n" +
				"        \"version\" : \"1.0\",\n" +
				"        \"releaseName\" : \"Spring Framework\",\n" +
				"        \"releaseDate\" : \"24-03-2004\"\n" +
				"    }";

		Message<String> inputMessage = CloudEventMessageBuilder
			.withData(payload)
			.setId(id)
			.setSource("https://spring.io/")
			.setType("org.springframework")
			.setHeader(MessageUtils.TARGET_PROTOCOL, CloudEventMessageUtils.Protocols.KAFKA)
			.build(CloudEventMessageUtils.AMQP_ATTR_PREFIX);

		assertThat(CloudEventMessageUtils.isCloudEvent(inputMessage)).isTrue();

		Message<?> message = ((Flux<Message<?>>) function.apply(Flux.just(inputMessage))).blockFirst();

		/*
		 * Validates that although user only deals with POJO, the framework recognizes
		 * both on input and output that it is dealing with Cloud Event and generates
		 * appropriate headers/attributes
		 */

		assertThat(CloudEventMessageUtils.isCloudEvent(message)).isTrue();
		assertThat(CloudEventMessageUtils.getType(message)).isEqualTo(SpringReleaseEvent.class.getName());
		assertThat(CloudEventMessageUtils.getSource(message)).isEqualTo(URI.create("http://spring.io/"));
		assertThat(message.getHeaders().get("ce_source")).isEqualTo(URI.create("http://spring.io/"));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testBinaryPojoToPojoDefaultOutputHeaderProviderReactiveMono() {
		Function<Object, Object> function = this.lookup("springReleaseReactiveMono", TestConfiguration.class);

		String id = UUID.randomUUID().toString();

		String payload = "{\n" +
				"        \"version\" : \"1.0\",\n" +
				"        \"releaseName\" : \"Spring Framework\",\n" +
				"        \"releaseDate\" : \"24-03-2004\"\n" +
				"    }";

		Message<String> inputMessage = CloudEventMessageBuilder
			.withData(payload)
			.setId(id)
			.setSource("https://spring.io/")
			.setType("org.springframework")
			.setHeader(MessageUtils.TARGET_PROTOCOL, CloudEventMessageUtils.Protocols.KAFKA)
			.build(CloudEventMessageUtils.AMQP_ATTR_PREFIX);

		assertThat(CloudEventMessageUtils.isCloudEvent(inputMessage)).isTrue();

		Message<?> message = ((Mono<Message<?>>) function.apply(Mono.just(inputMessage))).block();

		/*
		 * Validates that although user only deals with POJO, the framework recognizes
		 * both on input and output that it is dealing with Cloud Event and generates
		 * appropriate headers/attributes
		 */

		assertThat(CloudEventMessageUtils.isCloudEvent(message)).isTrue();
		assertThat(CloudEventMessageUtils.getType(message)).isEqualTo(SpringReleaseEvent.class.getName());
		assertThat(CloudEventMessageUtils.getSource(message)).isEqualTo(URI.create("http://spring.io/"));
		assertThat(message.getHeaders().get("ce_source")).isEqualTo(URI.create("http://spring.io/"));
	}


	// this kind of emulates that message came from Kafka
	@SuppressWarnings("unchecked")
	@Test
	public void testBinaryPojoToPojoDefaultOutputHeaderProviderWithPrefix() {
		Function<Object, Object> function = this.lookup("echo", TestConfiguration.class);

		String id = UUID.randomUUID().toString();

		Message<String> inputMessage = CloudEventMessageBuilder
			.withData("{\"name\":\"Ricky\"}")
			.setHeader("ce_id", id)
			.setHeader("ce_source", "https://spring.io/")
			.setHeader("ce_type", "org.springframework")
			.build();

		Message<Person> resultMessage = (Message<Person>) function.apply(inputMessage);

		/*
		 * Validates that although user only deals with POJO, the framework recognizes
		 * both on input and output that it is dealing with Cloud Event and generates
		 * appropriate headers/attributes
		 */
		assertThat(CloudEventMessageUtils.isCloudEvent(resultMessage)).isTrue();
		assertThat(CloudEventMessageUtils.getType(resultMessage)).isEqualTo(Person.class.getName());
		assertThat(CloudEventMessageUtils.getSource(resultMessage)).isEqualTo(URI.create("http://spring.io/"));
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

		Message<String> inputMessage = MessageBuilder
				.withPayload(payload)
				.setHeader(MessageHeaders.CONTENT_TYPE, CloudEventMessageUtils.APPLICATION_CLOUDEVENTS_VALUE + "+json")
				.build();

		assertThat(CloudEventMessageUtils.isCloudEvent(inputMessage)).isFalse();

		Message<SpringReleaseEvent> resultMessage = (Message<SpringReleaseEvent>) function.apply(inputMessage);
		assertThat(resultMessage.getPayload().getReleaseDate())
				.isEqualTo(new SimpleDateFormat("dd-MM-yyyy").parse("01-10-2006"));
		assertThat(resultMessage.getPayload().getVersion()).isEqualTo("2.0");
//		/*
//		 * Validates that although user only deals with POJO, the framework recognizes
//		 * both on input and output that it is dealing with Cloud Event and generates
//		 * appropriate headers/attributes
//		 */
		assertThat(CloudEventMessageUtils.isCloudEvent(resultMessage)).isTrue();
		assertThat(CloudEventMessageUtils.getType(resultMessage)).isEqualTo(SpringReleaseEvent.class.getName());
		assertThat(CloudEventMessageUtils.getSource(resultMessage)).isEqualTo(URI.create("http://spring.io/"));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testStructuredPojoToPojoMessageFunction() throws Exception {
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
		Function<Object, Object> function = this.lookup("springReleaseAsMessage", TestConfiguration.class);

		Message<String> inputMessage = MessageBuilder
				.withPayload(payload)
				.setHeader(MessageHeaders.CONTENT_TYPE, CloudEventMessageUtils.APPLICATION_CLOUDEVENTS_VALUE + "+json")
				.build();

		assertThat(CloudEventMessageUtils.isCloudEvent(inputMessage)).isFalse();

		Message<SpringReleaseEvent> resultMessage = (Message<SpringReleaseEvent>) function.apply(inputMessage);
		assertThat(resultMessage.getPayload().getReleaseDate())
				.isEqualTo(new SimpleDateFormat("dd-MM-yyyy").parse("01-10-2006"));
		assertThat(resultMessage.getPayload().getVersion()).isEqualTo("2.0");
//		/*
//		 * Validates that although user only deals with POJO, the framework recognizes
//		 * both on input and output that it is dealing with Cloud Event and generates
//		 * appropriate headers/attributes
//		 */
		assertThat(CloudEventMessageUtils.isCloudEvent(resultMessage)).isTrue();
		assertThat(CloudEventMessageUtils.getType(resultMessage)).isEqualTo(SpringReleaseEvent.class.getName());
		assertThat(CloudEventMessageUtils.getSource(resultMessage)).isEqualTo(URI.create("https://spring.release.event"));
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

		Message<String> inputMessage = MessageBuilder
				.withPayload(payload)
				.setHeader(MessageHeaders.CONTENT_TYPE, CloudEventMessageUtils.APPLICATION_CLOUDEVENTS_VALUE + "+json")
				.build();
		assertThat(CloudEventMessageUtils.isCloudEvent(inputMessage)).isFalse();

		Message<SpringReleaseEvent> resultMessage = (Message<SpringReleaseEvent>) function.apply(inputMessage);
		assertThat(resultMessage.getPayload().getReleaseDate())
				.isEqualTo(new SimpleDateFormat("dd-MM-yyyy").parse("01-10-2006"));
		assertThat(resultMessage.getPayload().getVersion()).isEqualTo("2.0");
		/*
		 * Validates that although user only deals with POJO, the framework recognizes
		 * both on input and output that it is dealing with Cloud Event and generates
		 * appropriate headers/attributes
		 */
		assertThat(CloudEventMessageUtils.isCloudEvent(resultMessage)).isTrue();
		assertThat(CloudEventMessageUtils.getType(resultMessage)).isEqualTo(SpringReleaseEvent.class.getName());
		assertThat(CloudEventMessageUtils.getSource(resultMessage)).isEqualTo(URI.create("http://spring.io/"));
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

		@Bean
		Function<Flux<SpringReleaseEvent>, Flux<SpringReleaseEvent>> springReleaseReactive() {
			return flux -> flux.map(event -> {
				try {
					event.setReleaseDate(new SimpleDateFormat("dd-MM-yyyy").parse("01-10-2006"));
					event.setVersion("2.0");
					return event;
				}
				catch (Exception e) {
					throw new IllegalArgumentException(e);
				}
			});
		}

		@Bean
		Function<Mono<SpringReleaseEvent>, Mono<SpringReleaseEvent>> springReleaseReactiveMono() {
			return mono -> mono.map(event -> {
				try {
					event.setReleaseDate(new SimpleDateFormat("dd-MM-yyyy").parse("01-10-2006"));
					event.setVersion("2.0");
					return event;
				}
				catch (Exception e) {
					throw new IllegalArgumentException(e);
				}
			});
		}

		@Bean
		Function<Message<SpringReleaseEvent>, Message<SpringReleaseEvent>> springReleaseAsMessage() {
			return message -> {
				SpringReleaseEvent updated = springRelease().apply(message.getPayload());
				return CloudEventMessageBuilder.withData(updated)
						.copyHeaders(message.getHeaders())
						.setSource("https://spring.release.event")
						.setType(SpringReleaseEvent.class.getName())
						.build();
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
