/*
 * Copyright 2020-present the original author or authors.
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the HTTP functions adapter for Google Cloud Functions.
 *
 * @author Dmitry Solomakha
 * @author Mike Eltsufin
 */

@ExtendWith(OutputCaptureExtension.class)
public class FunctionInvokerHttpTests {

	private static final Gson gson = new Gson();
	private HttpRequest request;
	private HttpResponse response;
	private BufferedWriter bufferedWriter;
	private StringWriter writer;

	@BeforeEach
	void testSetup() throws IOException {
		request = Mockito.mock(HttpRequest.class);
		response = Mockito.mock(HttpResponse.class);
		writer = new StringWriter();
		bufferedWriter = new BufferedWriter(writer);
		when(response.getWriter()).thenReturn(bufferedWriter);
	}

	@Test
	public void testHelloWorldSupplier() throws Exception {

		String expectedOutput = "Hello World!";
		FunctionInvoker handler = new FunctionInvoker(HelloWorldSupplier.class);

		handler.service(request, response);
		bufferedWriter.close();


		assertThat(writer.toString()).isEqualTo(gson.toJson(expectedOutput));


	}


	@Test
	public void testJsonInputFunction() throws Exception {

		FunctionInvoker handler = new FunctionInvoker(JsonInputFunction.class);

		String expectedOutput = "Thank you for sending the message: hello";
		IncomingRequest input = new IncomingRequest("hello");

		when(request.getReader()).thenReturn(new BufferedReader(new StringReader(gson.toJson(input))));
		handler.service(request, response);
		bufferedWriter.close();


		assertThat(writer.toString()).isEqualTo(gson.toJson(expectedOutput));
	}

	@Test
	public void testWithKanji() throws Exception {

		FunctionInvoker handler = new FunctionInvoker(JsonInputFunction.class);

		String expectedOutput = "Thank you for sending the message: 森林";
		IncomingRequest input = new IncomingRequest("森林");

		when(request.getReader()).thenReturn(new BufferedReader(new StringReader(gson.toJson(input))));
		handler.service(request, response);
		bufferedWriter.close();


		assertThat(writer.toString()).isEqualTo(gson.toJson(expectedOutput));
	}

	@Test
	public void testJsonInputOutputFunction() throws Exception {

		FunctionInvoker handler = new FunctionInvoker(JsonInputOutputFunction.class);

		OutgoingResponse expectedOutput = new OutgoingResponse("Thank you for sending the message: hello");
		IncomingRequest input = new IncomingRequest("hello");

		when(request.getReader()).thenReturn(new BufferedReader(new StringReader(gson.toJson(input))));
		handler.service(request, response);
		bufferedWriter.close();


		assertThat(writer.toString()).isEqualTo(gson.toJson(expectedOutput));


	}

	@Test
	public void testJsonInputConsumer_Background(CapturedOutput capturedOutput) throws Exception {

		FunctionInvoker handler = new FunctionInvoker(JsonInputConsumer.class);

		IncomingRequest input = new IncomingRequest("hello");

		when(request.getReader()).thenReturn(new BufferedReader(new StringReader(gson.toJson(input))));
		handler.service(request, response);
		bufferedWriter.close();

		assertThat(capturedOutput.toString()).contains("Thank you for sending the message: hello");

	}

	@Test
	public void testStatusCodeSet() throws Exception {

		FunctionInvoker handler = new FunctionInvoker(StatusCodeSupplier.class);
		String input = "hello";
		when(request.getReader()).thenReturn(new BufferedReader(new StringReader(gson.toJson(input))));
		handler.service(request, response);
		bufferedWriter.close();

		verify(response).setStatusCode(404);
		verify(response).setContentType("text/plain");
	}

	@Test
	public void testMultiValueHeaderSupplied() throws Exception {

		FunctionInvoker handler = new FunctionInvoker(MultiValueHeaderSupplier.class);
		String input = "hello";
		when(request.getReader()).thenReturn(new BufferedReader(new StringReader(gson.toJson(input))));
		handler.service(request, response);
		bufferedWriter.close();

		verify(response).appendHeader("multiValueHeader", "123,headerThing");
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
	protected static class StatusCodeSupplier {

		@Bean
		public Function<String, Message<String>> function() {

			String payload = "hello";

			Message<String> msg = MessageBuilder.withPayload(payload).setHeader("statusCode", 404).setHeader(MessageHeaders.CONTENT_TYPE, "text/plain")
				.build();

			return x -> msg;
		};

	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class })
	protected static class MultiValueHeaderSupplier {

		@Bean
		public Function<String, Message<String>> function() {

			String payload = "hello";
			List<Object> li = new ArrayList<Object>(asList(123, "headerThing"));

			Message<String> msg = MessageBuilder.withPayload(payload).setHeader("multiValueHeader", li)
				.build();

			return x -> msg;
		};

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

	public static class IncomingRequest {

		String message;

		public IncomingRequest(String message) {
			this.message = message;
		}

		public IncomingRequest() {
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}
	}

	public static class OutgoingResponse {

		String message;

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		OutgoingResponse(String message) {
			this.message = message;
		}

		public OutgoingResponse() {
		}

	}


}
