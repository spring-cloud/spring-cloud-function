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

package org.springframework.cloud.function.adapter.aws;

import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dimitry Declercq
 * @author Markus Gulden
 */
public class SpringBootApiGatewayRequestHandlerTests {

	private SpringBootApiGatewayRequestHandler handler;

	@AfterEach
	public void after() {
		System.clearProperty("function.name");
	}

	@Test
	public void supplierBean() {
		System.setProperty("function.name", "supplier");
		this.handler = new SpringBootApiGatewayRequestHandler(FunctionConfig.class);
		APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();

		Object output = this.handler.handleRequest(request, null);
		assertThat(output).isInstanceOf(APIGatewayProxyResponseEvent.class);
		assertThat(((APIGatewayProxyResponseEvent) output).getStatusCode())
				.isEqualTo(200);
		assertThat(((APIGatewayProxyResponseEvent) output).getBody())
				.isEqualTo("\"hello!\"");
	}

	@Test
	public void functionBean() {
		System.setProperty("function.name", "function");
		this.handler = new SpringBootApiGatewayRequestHandler(FunctionConfig.class);
		APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
		request.setBody("{\"value\":\"foo\"}");

		Object output = this.handler.handleRequest(request, null);
		assertThat(output).isInstanceOf(APIGatewayProxyResponseEvent.class);
		assertThat(((APIGatewayProxyResponseEvent) output).getStatusCode())
				.isEqualTo(200);
		assertThat(((APIGatewayProxyResponseEvent) output).getBody())
				.isEqualTo("{\"value\":\"FOO\"}");

		APIGatewayProxyRequestEvent bodyEncryptedRequest = new APIGatewayProxyRequestEvent();
		bodyEncryptedRequest.setBody(
				Base64.getEncoder().encodeToString("{\"value\":\"foo\"}".getBytes()));
		bodyEncryptedRequest.setIsBase64Encoded(true);

		output = this.handler.handleRequest(bodyEncryptedRequest, null);
		assertThat(output).isInstanceOf(APIGatewayProxyResponseEvent.class);
		assertThat(((APIGatewayProxyResponseEvent) output).getStatusCode())
				.isEqualTo(200);
		assertThat(((APIGatewayProxyResponseEvent) output).getBody())
				.isEqualTo("{\"value\":\"FOO\"}");
	}

	@Test
	public void consumerBean() {
		System.setProperty("function.name", "consumer");
		this.handler = new SpringBootApiGatewayRequestHandler(FunctionConfig.class);
		APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
		request.setBody("\"strVal\":\"test for consumer\"");

		Object output = this.handler.handleRequest(request, null);
		assertThat(output).isInstanceOf(APIGatewayProxyResponseEvent.class);
		assertThat(((APIGatewayProxyResponseEvent) output).getStatusCode())
				.isEqualTo(200);
	}

	@Test
	public void functionMessageBean() {
		this.handler = new SpringBootApiGatewayRequestHandler(
				FunctionMessageConfig.class);
		APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
		request.setBody("{\"value\":\"foo\"}");

		Object output = this.handler.handleRequest(request, null);
		assertThat(output).isInstanceOf(APIGatewayProxyResponseEvent.class);
		assertThat(((APIGatewayProxyResponseEvent) output).getStatusCode())
				.isEqualTo(200);
		assertThat(((APIGatewayProxyResponseEvent) output).getHeaders().get("spring"))
				.isEqualTo("cloud");
		assertThat(((APIGatewayProxyResponseEvent) output).getBody())
				.isEqualTo("{\"value\":\"FOO\"}");
	}


	@Test
	public void functionMessageBeanWithRequestParameters() {
		this.handler = new SpringBootApiGatewayRequestHandler(
				FunctionMessageEchoReqParametersConfig.class);
		APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
		request.setPathParameters(Collections.singletonMap("path", "pathValue"));
		request.setQueryStringParameters(Collections.singletonMap("query", "queryValue"));
		request.setHeaders(Collections.singletonMap("test-header", "headerValue"));
		request.setHttpMethod("GET");

		Object output = this.handler.handleRequest(request, null);
		assertThat(output).isInstanceOf(APIGatewayProxyResponseEvent.class);
		assertThat(((APIGatewayProxyResponseEvent) output).getStatusCode())
				.isEqualTo(200);
		assertThat(((APIGatewayProxyResponseEvent) output).getHeaders().get("path"))
				.isEqualTo("pathValue");
		assertThat(((APIGatewayProxyResponseEvent) output).getHeaders().get("query"))
				.isEqualTo("queryValue");
		assertThat(
				((APIGatewayProxyResponseEvent) output).getHeaders().get("test-header"))
						.isEqualTo("headerValue");
		assertThat(((APIGatewayProxyResponseEvent) output).getHeaders().get("httpMethod"))
				.isEqualTo("GET");
		assertThat(((APIGatewayProxyResponseEvent) output).getBody())
				.isEqualTo("{\"value\":\"body\"}");

	}

	@Test
	public void functionMessageBeanWithEmptyResponse() {
		this.handler = new SpringBootApiGatewayRequestHandler(
				FunctionMessageConsumerConfig.class);
		APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();

		Object output = this.handler.handleRequest(request, null);
		assertThat(output).isInstanceOf(APIGatewayProxyResponseEvent.class);
		assertThat(((APIGatewayProxyResponseEvent) output).getStatusCode())
				.isEqualTo(200);
		assertThat(((APIGatewayProxyResponseEvent) output).getBody()).isNull();
	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class,
			JacksonAutoConfiguration.class })
	protected static class FunctionConfig {

		@Bean
		public Function<Foo, Bar> function() {
			return foo -> new Bar(foo.getValue().toUpperCase());
		}

		@Bean
		public Consumer<String> consumer() {
			return v -> System.out.println(v);
		}

		@Bean
		public Supplier<String> supplier() {
			return () -> "hello!";
		}

	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class,
			JacksonAutoConfiguration.class })
	protected static class FunctionMessageConfig {

		@Bean
		public Function<Message<Foo>, Message<Bar>> function() {
			return (foo -> {
				Map<String, Object> headers = Collections.singletonMap("spring", "cloud");
				return new GenericMessage<>(
						new Bar(foo.getPayload().getValue().toUpperCase()), headers);
			});
		}

	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class,
			JacksonAutoConfiguration.class })
	protected static class FunctionMessageEchoReqParametersConfig {

		@Bean
		public Function<Message<Foo>, Message<Bar>> function() {
			return (message -> {
				Map<String, Object> headers = new HashMap<>();
				headers.put("path", message.getHeaders().get("path"));
				headers.put("query", message.getHeaders().get("query"));
				headers.put("test-header", message.getHeaders().get("test-header"));
				headers.put("httpMethod", message.getHeaders().get("httpMethod"));
				return new GenericMessage<>(new Bar("body"), headers);
			});
		}

	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class,
			JacksonAutoConfiguration.class })
	protected static class FunctionMessageConsumerConfig {

		@Bean
		public Consumer<Message<Foo>> function() {
			return (foo -> {
			});
		}

	}

	protected static class Foo {

		private String value;

		public String getValue() {
			return this.value;
		}

		public void setValue(String value) {
			this.value = value;
		}

	}

	protected static class Bar {

		private String value;

		public Bar() {
		}

		public Bar(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

		public void setValue(String value) {
			this.value = value;
		}

	}

}
