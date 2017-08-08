/*
 * Copyright 2016-2017 the original author or authors.
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
package org.springframework.cloud.stream.binder.servlet.test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext
public class SseSourceMessageChannelBinderTests {

	private static Log log = LogFactory.getLog(SseSourceMessageChannelBinderTests.class);

	@Autowired
	private Source source;

	private CountDownLatch latch = new CountDownLatch(1);

	private RestTemplate rest = new RestTemplate();

	@LocalServerPort
	private int port;

	private String message = null;

	@Before
	public void init() throws Exception {
		rest.getForEntity(
				new URI("http://localhost:" + port + "/stream/output?purge=true"),
				String.class);
	}

	@Test
	public void supplier() throws Exception {
		source.output().send(MessageBuilder.withPayload("hello").build());
		rest.getInterceptors().add(new NonClosingInterceptor());
		ResponseEntity<String> response = rest.execute(
				new URI("http://localhost:" + port + "/stream/output"), HttpMethod.GET,
				request -> request.getHeaders()
						.setAccept(Arrays.asList(MediaType.TEXT_EVENT_STREAM)),
				this::extract);
		assertThat(response.getHeaders().getContentType())
				.isGreaterThan(MediaType.TEXT_EVENT_STREAM);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo("data:hello\n\n");
	}

	@Test
	public void lateSending() throws Exception {
		message = "world";
		rest.getInterceptors().add(new NonClosingInterceptor());
		ResponseEntity<String> response = rest.execute(
				new URI("http://localhost:" + port + "/stream/output"), HttpMethod.GET,
				request -> request.getHeaders()
						.setAccept(Arrays.asList(MediaType.TEXT_EVENT_STREAM)),
				this::extract);
		assertThat(response.getHeaders().getContentType())
				.isGreaterThan(MediaType.TEXT_EVENT_STREAM);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo("data:world\n\n");
	}

	@SpringBootApplication
	@EnableBinding(Source.class)
	protected static class TestConfiguration {
	}

	private ResponseEntity<String> extract(ClientHttpResponse response)
			throws IOException {
		if (message != null) {
			// Once there is an incoming request we can send a message to it
			source.output().send(MessageBuilder.withPayload(message).build());
		}
		byte[] bytes = new byte[1024];
		StringBuilder builder = new StringBuilder();
		int read = 0;
		while (read >= 0
				&& StringUtils.countOccurrencesOf(builder.toString(), "\n") < 2) {
			read = response.getBody().read(bytes, 0, bytes.length);
			if (read > 0) {
				latch.countDown();
				builder.append(new String(bytes, 0, read));
			}
			log.debug("Building: " + builder);
		}
		log.debug("Done: " + builder);
		return ResponseEntity.status(response.getStatusCode())
				.headers(response.getHeaders()).body(builder.toString());
	}

	/**
	 * Special interceptor that prevents the response from being closed and allows us to
	 * assert on the contents of an event stream.
	 */
	private class NonClosingInterceptor implements ClientHttpRequestInterceptor {

		private class NonClosingResponse implements ClientHttpResponse {

			private ClientHttpResponse delegate;

			public NonClosingResponse(ClientHttpResponse delegate) {
				this.delegate = delegate;
			}

			@Override
			public InputStream getBody() throws IOException {
				return delegate.getBody();
			}

			@Override
			public HttpHeaders getHeaders() {
				return delegate.getHeaders();
			}

			@Override
			public HttpStatus getStatusCode() throws IOException {
				return delegate.getStatusCode();
			}

			@Override
			public int getRawStatusCode() throws IOException {
				return delegate.getRawStatusCode();
			}

			@Override
			public String getStatusText() throws IOException {
				return delegate.getStatusText();
			}

			@Override
			public void close() {
			}

		}

		@Override
		public ClientHttpResponse intercept(HttpRequest request, byte[] body,
				ClientHttpRequestExecution execution) throws IOException {
			return new NonClosingResponse(execution.execute(request, body));
		}

	}

}
