/*
 * Copyright 2012-2015 the original author or authors.
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

package org.springframework.cloud.function.web.flux;

import org.junit.Test;

import org.springframework.http.MediaType;
import org.springframework.mock.http.MockHttpInputMessage;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
public class FluxHttpMessageConverterTests {

	private FluxHttpMessageConverter converter = new FluxHttpMessageConverter();

	private Class<Flux<Object>> type = null;

	@Test
	public void newlines() throws Exception {
		MockHttpInputMessage message = new MockHttpInputMessage("foo\nbar".getBytes());
		assertThat(converter.read(type, message).collectList().block()).contains("foo",
				"bar");
	}

	@Test
	public void sse() throws Exception {
		MockHttpInputMessage message = new MockHttpInputMessage(
				"data:foo\n\ndata:bar".getBytes());
		message.getHeaders().setContentType(MediaType.valueOf("text/event-stream"));
		assertThat(converter.read(type, message).collectList().block()).contains("foo",
				"bar");
	}

	@Test
	public void jsonStream() throws Exception {
		MockHttpInputMessage message = new MockHttpInputMessage(
				"{\"value\":\"foo\"}{\"value\":\"barrier\"}".getBytes());
		message.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		assertThat(converter.read(type, message).collectList().block())
				.contains("{\"value\":\"foo\"}", "{\"value\":\"barrier\"}");
	}

	@Test
	public void jsonStreamWhitespace() throws Exception {
		MockHttpInputMessage message = new MockHttpInputMessage(
				"{\"value\":\"foo\"} {\"value\":\"barrier\"}  ".getBytes());
		message.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		assertThat(converter.read(type, message).collectList().block())
				.contains("{\"value\":\"foo\"}", "{\"value\":\"barrier\"}");
	}

	@Test
	public void jsonStreamNewline() throws Exception {
		MockHttpInputMessage message = new MockHttpInputMessage(
				"{\"value\":\"foo\"}\n{\"value\":\"barrier\"}".getBytes());
		message.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		assertThat(converter.read(type, message).collectList().block())
				.contains("{\"value\":\"foo\"}", "{\"value\":\"barrier\"}");
	}

	@Test
	public void jsonArray() throws Exception {
		MockHttpInputMessage message = new MockHttpInputMessage(
				"[{\"value\":\"foo\"},{\"value\":\"barrier\"}]".getBytes());
		message.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		assertThat(converter.read(type, message).collectList().block())
				.contains("{\"value\":\"foo\"}", "{\"value\":\"barrier\"}");
	}

	@Test
	public void jsonArrayWhitespace() throws Exception {
		MockHttpInputMessage message = new MockHttpInputMessage(
				"[{\"value\":\"foo\"}, {\"value\":\"barrier\"}]  ".getBytes());
		message.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		assertThat(converter.read(type, message).collectList().block())
				.contains("{\"value\":\"foo\"}", "{\"value\":\"barrier\"}");
	}

	@Test
	public void jsonArrayNewline() throws Exception {
		MockHttpInputMessage message = new MockHttpInputMessage(
				"[{\"value\":\"foo\"},\n{\"value\":\"barrier\"}]".getBytes());
		message.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		assertThat(converter.read(type, message).collectList().block())
				.contains("{\"value\":\"foo\"}", "{\"value\":\"barrier\"}");
	}

}
