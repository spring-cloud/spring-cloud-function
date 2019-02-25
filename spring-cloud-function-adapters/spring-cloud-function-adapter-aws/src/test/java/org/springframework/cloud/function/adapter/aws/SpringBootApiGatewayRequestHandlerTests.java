/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.adapter.aws;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.Test;

import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 */
public class SpringBootApiGatewayRequestHandlerTests {

	private SpringBootApiGatewayRequestHandler handler;

	@Test
	public void functionBean() {
		this.handler = new SpringBootApiGatewayRequestHandler(FunctionConfig.class);
		APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
		request.setBody("{\"value\":\"foo\"}");

		Object output = this.handler.handleRequest(request, null);
		assertThat(output).isInstanceOf(APIGatewayProxyResponseEvent.class);
		assertThat(((APIGatewayProxyResponseEvent) output).getStatusCode())
				.isEqualTo(200);
		assertThat(((APIGatewayProxyResponseEvent) output).getBody())
				.isEqualTo("{\"value\":\"FOO\"}");
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

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class,
			JacksonAutoConfiguration.class })
	protected static class FunctionConfig {

		@Bean
		public Function<Foo, Bar> function() {
			return foo -> new Bar(foo.getValue().toUpperCase());
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
