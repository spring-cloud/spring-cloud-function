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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the HTTP functions adapter for Google Cloud Functions.
 *
 * @author Dmitry Solomakha
 * @author Mike Eltsufin
 */
public class FunctionInvokerHttpTests {

	private static final Gson gson = new Gson();

	@Test
	public void testHelloWorldSupplier() throws Exception {
		testHttpFunction(HelloWorldSupplier.class, null, "Hello World!");
	}

	@Test
	public void testJsonInputFunction() throws Exception {
		testHttpFunction(JsonInputFunction.class, new IncomingRequest("hello"),
				"Thank you for sending the message: hello");
	}

	@Test
	public void testJsonInputOutputFunction() throws Exception {
		testHttpFunction(JsonInputOutputFunction.class, new IncomingRequest("hello"),
				new OutgoingResponse("Thank you for sending the message: hello"));
	}

	@Test
	public void testJsonInputConsumer_Background() throws Exception {
		testHttpFunction(JsonInputConsumer.class, new IncomingRequest("hello"), null);
	}

	private <I, O> void testHttpFunction(Class<?> configurationClass, I input, O expectedOutput) throws Exception {
		try (FunctionInvoker handler = new FunctionInvoker(configurationClass);) {

			HttpRequest request = Mockito.mock(HttpRequest.class);

			if (input != null) {
				when(request.getReader()).thenReturn(new BufferedReader(new StringReader(gson.toJson(input))));
			}

			HttpResponse response = Mockito.mock(HttpResponse.class);
			StringWriter writer = new StringWriter();

			BufferedWriter bufferedWriter = new BufferedWriter(writer);
			when(response.getWriter()).thenReturn(bufferedWriter);
			handler.service(request, response);

			// Closing the writer is done by the Framework/caller.
			bufferedWriter.close();

			if (expectedOutput != null) {
				assertThat(writer.toString()).isEqualTo(gson.toJson(expectedOutput));
			}
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
