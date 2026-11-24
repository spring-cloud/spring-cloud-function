/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.cloud.function.web.source;

import java.util.List;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.web.FunctionHttpProperties;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the header handling of {@link HttpSupplier}.
 *
 * @author Oleg Zhurakousky
 */
class HttpSupplierTests {

	@Test
	void sourceResponseHeadersAreSanitizedBeforeBeingForwarded() {
		FunctionHttpProperties httpProperties = new FunctionHttpProperties();
		httpProperties.setIgnoredHeaders(List.of("X-Internal-Token"));

		Message<?> message = (Message<?>) supplier(true, httpProperties).get().blockFirst();

		assertThat(message.getPayload()).isEqualTo("hello");
		assertThat(message.getHeaders()).containsEntry("x-trace-id", "trace-123")
				.doesNotContainKeys("set-cookie", "authorization", "x-internal-token", "location");
	}

	@Test
	void noHeadersAreForwardedWhenIncludeHeadersIsDisabled() {
		Object value = supplier(false, new FunctionHttpProperties()).get().blockFirst();

		assertThat(value).isEqualTo("hello");
	}

	private static HttpSupplier supplier(boolean includeHeaders, FunctionHttpProperties httpProperties) {
		WebClient client = WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
				.header("X-Trace-Id", "trace-123")
				.header("X-Internal-Token", "secret")
				.header("Set-Cookie", "session=abc")
				.header("Authorization", "Bearer abc")
				.header("Location", "/elsewhere")
				.header("Content-Type", "text/plain")
				.body("hello").build())).build();
		ExporterProperties properties = new ExporterProperties();
		properties.getSource().setUrl("http://localhost/source");
		properties.getSource().setType(String.class);
		properties.getSource().setIncludeHeaders(includeHeaders);
		return new HttpSupplier(client, properties, httpProperties);
	}

}
