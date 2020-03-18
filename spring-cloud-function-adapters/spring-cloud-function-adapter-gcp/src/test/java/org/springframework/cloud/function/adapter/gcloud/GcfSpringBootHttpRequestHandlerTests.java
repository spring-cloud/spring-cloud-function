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

package org.springframework.cloud.function.adapter.gcloud;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import org.junit.After;
import org.junit.Test;
import org.mockito.Mockito;

import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author Dmitry Solomakha
 */
public class GcfSpringBootHttpRequestHandlerTests {
	HttpRequest request = Mockito.mock(HttpRequest.class);
	HttpResponse response = Mockito.mock(HttpResponse.class);

	private GcfSpringBootHttpRequestHandler<?> handler = null;
	public static final Gson GSON = new Gson();

	<O> GcfSpringBootHttpRequestHandler<O> handler(Class<?> config) {
		GcfSpringBootHttpRequestHandler<O> handler =
			new GcfSpringBootHttpRequestHandler<O>(config);
		this.handler = handler;
		return handler;
	}

	@Test
	public void testWithBody() throws Exception {
		GcfSpringBootHttpRequestHandler<Foo> handler = handler(FunctionMessageBodyConfig.class);

		StringReader foo = new StringReader(GSON.toJson(new Foo("foo")));
		when(request.getReader()).thenReturn(new BufferedReader(foo));

		StringWriter writer = new StringWriter();
		handler.service(request, new HttpResponseImpl(new BufferedWriter(writer)));

		assertThat(GSON.fromJson(writer.toString(), Bar.class)).isEqualTo(new Bar("FOO"));
	}

	@Test
	public void testWithRequestParameters() throws Exception {
		GcfSpringBootHttpRequestHandler<Foo> handler = handler(FunctionMessageEchoReqParametersConfig.class);

		when(request.getReader()).thenReturn(new BufferedReader(new StringReader("")));
		when(request.getUri()).thenReturn("http://localhost:8080/pathValue");
		when(request.getPath()).thenReturn("/pathValue");
		when(request.getHeaders())
			.thenReturn(Collections.singletonMap("test-header", Collections.singletonList("headerValue")));
		when(request.getMethod()).thenReturn("GET");

		StringWriter writer = new StringWriter();
		HttpResponseImpl response = new HttpResponseImpl(new BufferedWriter(writer));
		handler.service(request, response);

		assertThat(response.statusCode).isEqualTo(200);
		assertThat(response.headers.get("path")).containsExactly("/pathValue");
		assertThat(response.headers.get("test-header")).containsExactly("headerValue");
		assertThat(GSON.fromJson(writer.toString(), Bar.class)).isEqualTo(new Bar("body"));
	}

	@Test
	public void testWithEmptyBody() throws Exception {
		GcfSpringBootHttpRequestHandler<Foo> handler = handler(FunctionMessageConsumerConfig.class);

		when(request.getReader()).thenReturn(new BufferedReader(new StringReader("")));

		StringWriter writer = new StringWriter();
		HttpResponseImpl response = new HttpResponseImpl(new BufferedWriter(writer));
		handler.service(request, response);

		assertThat(response.statusCode).isEqualTo(200);
		assertThat(writer.toString()).isEqualTo("");
	}

	@After
	public void close() {
		if (this.handler != null) {
			this.handler.close();
		}
	}

	@Configuration
	@Import({ContextFunctionCatalogAutoConfiguration.class})
	protected static class FunctionMessageBodyConfig {

		@Bean
		public Function<Message<Foo>, Message<Bar>> function() {
			return (foo -> {
				Map<String, Object> headers = new HashMap<>();
				return new GenericMessage<>(
					new Bar(foo.getPayload().getValue().toUpperCase()), headers);
			});
		}

	}

	@Configuration
	@Import({ContextFunctionCatalogAutoConfiguration.class})
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
	@Import({ContextFunctionCatalogAutoConfiguration.class})
	protected static class FunctionMessageConsumerConfig {

		@Bean
		public Consumer<Message<Foo>> function() {
			return (foo -> { });
		}
	}

	private static class Foo {

		private String value;

		Foo() {
		}

		Foo(String value) {
			this.value = value;
		}

		public String lowercase() {
			return this.value.toLowerCase();
		}

		public String uppercase() {
			return this.value.toUpperCase();
		}

		public String getValue() {
			return this.value;
		}

		public void setValue(String value) {
			this.value = value;
		}

	}

	private static class Bar {

		private String value;

		Bar() {
		}

		Bar(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

		public void setValue(String value) {
			this.value = value;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			Bar bar = (Bar) o;
			return Objects.equals(getValue(), bar.getValue());
		}

		@Override
		public int hashCode() {
			return Objects.hash(getValue());
		}
	}

	private static class HttpResponseImpl implements HttpResponse {

		int statusCode;

		String contentType;

		BufferedWriter writer;

		HttpResponseImpl(BufferedWriter writer) {
			this.writer = writer;
		}

		Map<String, List<String>> headers = new HashMap<>();

		@Override
		public void setStatusCode(int code) {
			statusCode = code;
		}

		@Override
		public void setStatusCode(int code, String message) {
			statusCode = code;
		}

		@Override
		public void setContentType(String contentType) {
			this.contentType = contentType;
		}

		@Override
		public Optional<String> getContentType() {
			return Optional.ofNullable(contentType);
		}

		@Override
		public void appendHeader(String header, String value) {
			headers.computeIfAbsent(header, x -> new ArrayList<>()).add(value);
		}

		@Override
		public Map<String, List<String>> getHeaders() {
			return headers;
		}

		@Override
		public OutputStream getOutputStream() throws IOException {
			throw new RuntimeException("unsupported!");
		}

		@Override
		public BufferedWriter getWriter() throws IOException {
			return writer;
		}
	}
}
