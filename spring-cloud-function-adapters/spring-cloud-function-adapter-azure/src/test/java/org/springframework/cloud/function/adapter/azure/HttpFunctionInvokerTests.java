/*
 * Copyright 2019-present the original author or authors.
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

package org.springframework.cloud.function.adapter.azure;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpResponseMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.function.adapter.azure.helper.HttpRequestMessageStub;
import org.springframework.cloud.function.adapter.azure.helper.TestExecutionContext;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Markus Gulden
 */
public class HttpFunctionInvokerTests {

	private HttpFunctionInvoker<?> handler = null;

	<I> HttpFunctionInvoker<I> handler(Class<?> config) {
		HttpFunctionInvoker<I> handler = new HttpFunctionInvoker<I>(
				config);
		this.handler = handler;
		return handler;
	}

	@Test
	public void testWithBody() {
		HttpFunctionInvoker<Foo> handler = handler(
				FunctionMessageBodyConfig.class);
		HttpRequestMessageStub<Foo> request = new HttpRequestMessageStub<Foo>();
		request.setBody(new Foo("foo"));

		HttpResponseMessage response = handler.handleRequest(request,
				new TestExecutionContext("uppercase"));

		assertThat(response.getBody()).isInstanceOf(Bar.class);
		assertThat(response.getStatusCode()).isEqualTo(200);
		Bar body = (Bar) response.getBody();
		assertThat(body.getValue()).isEqualTo("FOO");
	}

	@Test
	public void testWithRequestParameters() throws URISyntaxException {
		HttpFunctionInvoker<Foo> handler = handler(
				FunctionMessageEchoReqParametersConfig.class);
		HttpRequestMessageStub<Foo> request = new HttpRequestMessageStub<Foo>();
		request.setUri(new URI("http://localhost:8080/pathValue"));
		request.setHeaders(Collections.singletonMap("test-header", "headerValue"));
		request.setQueryParameters(Collections.singletonMap("query", "queryValue"));
		request.setHttpMethod(HttpMethod.GET);

		HttpResponseMessage response = handler.handleRequest(request,
				new TestExecutionContext("uppercase"));

		assertThat(response.getStatusCode()).isEqualTo(200);
		assertThat(response.getHeader("path")).isEqualTo("/pathValue");
		assertThat(response.getHeader("query")).isEqualTo("queryValue");
		assertThat(response.getHeader("test-header")).isEqualTo("headerValue");
		Bar body = (Bar) response.getBody();
		assertThat(body.getValue()).isEqualTo("body");
	}

	@Test
	public void testWithEmptyBody() {
		HttpFunctionInvoker<Foo> handler = handler(
				FunctionMessageConsumerConfig.class);
		HttpRequestMessageStub<Foo> request = new HttpRequestMessageStub<Foo>();

		HttpResponseMessage response = handler.handleRequest(request,
				new TestExecutionContext("uppercase"));

		assertThat(response.getStatusCode()).isEqualTo(200);
		Bar body = (Bar) response.getBody();
		assertThat(body).isNull();
	}

	@AfterEach
	public void close() throws IOException {
		if (this.handler != null) {
			this.handler.close();
		}
	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class })
	protected static class FunctionMessageBodyConfig {

		@Bean
		public Function<Message<Foo>, Message<Bar>> function() {
			return (foo -> {
				Map<String, Object> headers = new HashMap<>();
				return new GenericMessage<>(
						new Bar(foo.getPayload().getValue().toUpperCase(Locale.ROOT)), headers);
			});
		}

	}

	@Configuration
	@Import({ ContextFunctionCatalogAutoConfiguration.class })
	protected static class FunctionMessageEchoReqParametersConfig {

		@Bean
		public Function<Message<Void>, Message<Bar>> function() {
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
	@Import({ ContextFunctionCatalogAutoConfiguration.class })
	protected static class FunctionMessageConsumerConfig {

		@Bean
		public Consumer<Message<Foo>> function() {
			return (foo -> {
			});
		}

	}
}
