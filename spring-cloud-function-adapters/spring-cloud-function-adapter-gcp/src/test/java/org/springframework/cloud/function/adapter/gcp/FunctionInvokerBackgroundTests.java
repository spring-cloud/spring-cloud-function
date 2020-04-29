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

package org.springframework.cloud.function.adapter.gcp;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.gson.Gson;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;

import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the background functions adapter for Google Cloud Functions.
 *
 * @author Dmitry Solomakha
 * @author Mike Eltsufin
 */
public class FunctionInvokerBackgroundTests {

	private static final Gson gson = new Gson();

	@Rule
	public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

	private static final String DROPPED_LOG_PREFIX = "Dropping background function result: ";

	@Test
	public void testHelloWorldSupplier_Background() throws Exception {
		testBackgroundFunction(HelloWorldSupplier.class, null, "Hello World!", null, null);
	}

	@Test
	public void testJsonInputFunction_Background() throws Exception {
		testBackgroundFunction(JsonInputFunction.class, new IncomingRequest("hello"),
				"Thank you for sending the message: hello", null, null);
	}

	@Test
	public void testJsonInputOutputFunction_Background() throws Exception {
		testBackgroundFunction(JsonInputOutputFunction.class, new IncomingRequest("hello"),
				new OutgoingResponse("Thank you for sending the message: hello"), null, null);
	}

	@Test
	public void testJsonInputConsumer() throws Exception {
		testBackgroundFunction(JsonInputConsumer.class, new IncomingRequest("hello"), null,
				"Thank you for sending the message: hello", null);
	}

	@Test
	public void testPubSubBackgroundFunction_PubSub() throws Exception {
		PubSubMessage pubSubMessage = new PubSubMessage();
		pubSubMessage.setData("hello");
		testBackgroundFunction(PubsubBackgroundFunction.class, pubSubMessage, null,
				"Thank you for sending the message: hello", "google.pubsub.topic.publish");
	}

	@Test
	public void testPubSubBackgroundFunction_PubSubPayload() throws Exception {
		PubSubMessage pubSubMessage = new PubSubMessage();
		IncomingRequest message = new IncomingRequest("Hello");
		pubSubMessage.setData(gson.toJson(message));
		testBackgroundFunction(PubsubBackgroundFunctionPayload.class, pubSubMessage, null,
				"Thank you for sending the message: Hello", "google.pubsub.topic.publish");
	}

	@Test
	public void testPubSubBackgroundFunction_StringMessage() throws Exception {
		PubSubMessage pubSubMessage = new PubSubMessage();
		pubSubMessage.setMessageId("1234");
		pubSubMessage.setData("Hello");
		testBackgroundFunction(PubsubBackgroundFunctionStringMessage.class, pubSubMessage, null,
				"Message: Hello; Type: google.pubsub.topic.publish; Message ID: 1234", "google.pubsub.topic.publish");
	}

	@Test
	public void testPubSubBackgroundFunction_PubSubMessage() throws Exception {
		PubSubMessage pubSubMessage = new PubSubMessage();
		pubSubMessage.setMessageId("1234");
		pubSubMessage.setData("Hello");
		testBackgroundFunction(PubsubBackgroundFunctionPubSubMessage.class, pubSubMessage, null,
				"Message: Hello; Type: google.pubsub.topic.publish; Message ID: 1234", "google.pubsub.topic.publish");
	}

	private <I, O> void testBackgroundFunction(Class<?> configurationClass, I input, O expectedResult,
			String expectedSysOut, String eventType) {

		FunctionInvoker handler = new FunctionInvoker(configurationClass);

		handler.accept(gson.toJson(input), new Context(null, null, eventType, null));

		// verify function sysout statements
		if (expectedSysOut != null) {
			assertThat(systemOutRule.getLog()).contains(expectedSysOut);
		}

		// verify that if function had a return type, it was logged as being dropped
		if (expectedResult != null) {
			assertThat(systemOutRule.getLog()).contains(DROPPED_LOG_PREFIX + gson.toJson(expectedResult));
		}
		else {
			assertThat(systemOutRule.getLog()).doesNotContain(DROPPED_LOG_PREFIX);
		}

	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class })
	protected static class HelloWorldSupplier {

		@Bean
		public Supplier<String> supplier() {
			return () -> "Hello World!";
		}

	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class })
	protected static class JsonInputFunction {

		@Bean
		public Function<IncomingRequest, String> function() {
			return (in) -> "Thank you for sending the message: " + in.message;
		}

	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class })
	protected static class JsonInputOutputFunction {

		@Bean
		public Function<IncomingRequest, Message<OutgoingResponse>> function() {
			return (in) -> {
				return MessageBuilder
						.withPayload(new OutgoingResponse("Thank you for sending the message: " + in.message))
						.setHeader("foo", "bar").build();
			};
		}

	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class })
	protected static class JsonInputConsumer {

		@Bean
		public Consumer<IncomingRequest> function() {
			return (in) -> System.out.println("Thank you for sending the message: " + in.message);
		}

	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class })
	protected static class PubsubBackgroundFunction {

		@Bean
		public Consumer<PubSubMessage> consumer() {
			return (in) -> System.out.println("Thank you for sending the message: " + in.getData());
		}

	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class })
	protected static class PubsubBackgroundFunctionPayload {

		@Bean
		public Consumer<IncomingRequest> consumerPayload() {
			return (in) -> {
				System.out.println("Thank you for sending the message: " + in.message);
			};
		}

		@Bean
		public MessageConverter messageToIncomingRequestConverter(JsonMapper mapper) {
			return new AbstractMessageConverter() {

				@Override
				protected boolean supports(Class<?> aClass) {
					return aClass == IncomingRequest.class;
				}

				@Override
				protected Object convertFromInternal(Message<?> message, Class<?> targetClass, Object conversionHint) {
					PubSubMessage pubSubMessage = mapper.fromJson(message.getPayload(), PubSubMessage.class);
					return mapper.fromJson(pubSubMessage.getData(), IncomingRequest.class);
				}
			};
		}

	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class })
	protected static class PubsubBackgroundFunctionStringMessage {

		@Bean
		public Consumer<Message<String>> consumeStringMessage(JsonMapper mapper) {
			return (message) -> {
				PubSubMessage pubSubMessage = mapper.fromJson(message.getPayload(), PubSubMessage.class);
				String payload = pubSubMessage.getData();

				String eventType = ((Context) message.getHeaders().get("gcf_context")).eventType();
				String messageId = pubSubMessage.getMessageId();
				System.out.println("Message: " + payload + "; Type: " + eventType + "; Message ID: " + messageId);
			};
		}
	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class })
	protected static class PubsubBackgroundFunctionPubSubMessage {

		@Bean
		public Consumer<Message<PubSubMessage>> consumePubSubMessage() {
			return (message) -> {
				String payload = message.getPayload().getData();
				String eventType = ((Context) message.getHeaders().get("gcf_context")).eventType();
				String messageId = message.getPayload().getMessageId();
				System.out.println("Message: " + payload + "; Type: " + eventType + "; Message ID: " + messageId);
			};
		}

	}

	private static class IncomingRequest {

		String message;

		IncomingRequest(String message) {
			this.message = message;
		}

	}

	private static class OutgoingResponse {

		String message;

		OutgoingResponse(String message) {
			this.message = message;
		}

	}

}
