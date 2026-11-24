/*
 * Copyright 2012-present the original author or authors.
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

package org.springframework.cloud.function.web.util;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HeaderUtils}.
 *
 * @author Dhionson Plaster Moreira
 */
class HeaderUtilsTests {

	@Test
	void fromMessageFiltersInternalAndCustomHeadersCaseInsensitively() {
		MessageHeaders messageHeaders = MessageBuilder.withPayload("payload")
				.setHeader("X-Trace-Id", "trace-123")
				.setHeader("Authorization", "secret")
				.setHeader("Content-Length", 12)
				.setHeader("X-Internal", "hidden")
				.build().getHeaders();

		HttpHeaders result = HeaderUtils.fromMessage(messageHeaders, List.of("x-internal"));

		assertThat(result.getFirst("x-trace-id")).isEqualTo("trace-123");
		assertThat(result.headerNames()).doesNotContain("id", "authorization", "content-length", "x-internal");
	}

	@Test
	void fromMessageFiltersCustomHeadersConfiguredInMixedCase() {
		MessageHeaders messageHeaders = MessageBuilder.withPayload("payload")
				.setHeader("X-Trace-Id", "trace-123")
				.setHeader("X-Internal-Token", "secret")
				.build().getHeaders();

		HttpHeaders result = HeaderUtils.fromMessage(messageHeaders, List.of("X-Internal-Token"));

		assertThat(result.getFirst("x-trace-id")).isEqualTo("trace-123");
		assertThat(result.headerNames()).doesNotContain("x-internal-token");
	}

	@Test
	void sanitizeRemovesCustomHeadersConfiguredInMixedCase() {
		HttpHeaders request = new HttpHeaders();
		request.add("X-Trace-Id", "trace-123");
		request.add("X-Internal-Token", "secret");
		request.add("X-Request-Only", "request-value");

		HttpHeaders result = HeaderUtils.sanitize(request, List.of("X-Internal-Token"), List.of("X-Request-Only"));

		assertThat(result.getFirst("x-trace-id")).isEqualTo("trace-123");
		assertThat(result.headerNames()).doesNotContain("x-internal-token", "x-request-only");
	}

	@Test
	void sanitizeDoesNotEchoHeadersThatChangeResponseInterpretation() {
		HttpHeaders request = new HttpHeaders();
		request.add("X-Trace-Id", "trace-123");
		for (String name : List.of("Content-Encoding", "Content-Disposition", "Content-Location", "Content-Range",
				"Location", "Refresh", "Link", "WWW-Authenticate", "Connection", "Keep-Alive", "Upgrade", "TE",
				"Trailer")) {
			request.add(name, "value");
		}

		HttpHeaders result = HeaderUtils.sanitize(request);

		assertThat(result.headerNames()).containsExactly("x-trace-id");
	}

	@Test
	void fromMessageStillAllowsAFunctionToSetLocationAndContentDisposition() {
		MessageHeaders messageHeaders = MessageBuilder.withPayload("payload")
				.setHeader("Location", "/new")
				.setHeader("Content-Disposition", "attachment")
				.build().getHeaders();

		HttpHeaders result = HeaderUtils.fromMessage(messageHeaders);

		assertThat(result.getFirst("location")).isEqualTo("/new");
		assertThat(result.getFirst("content-disposition")).isEqualTo("attachment");
	}

	@Test
	void fromMessageConvertsValuesAndUsesLastValueForMultiValueHeader() {
		MessageHeaders messageHeaders = MessageBuilder.withPayload("payload")
				.setHeader("X-Count", 42)
				.setHeader("X-Values", List.of("first", "last"))
				.build().getHeaders();

		HttpHeaders result = HeaderUtils.fromMessage(messageHeaders);

		assertThat(result.getFirst("x-count")).isEqualTo("42");
		assertThat(result.getFirst("x-values")).isEqualTo("last");
	}

	@Test
	void sanitizeRemovesIgnoredRequestOnlyAndCustomHeadersCaseInsensitively() {
		HttpHeaders request = new HttpHeaders();
		request.add("X-Trace-Id", "trace-123");
		request.add("Authorization", "secret");
		request.add("Host", "example.org");
		request.add("X-Internal", "hidden");
		request.add("X-Request-Only", "request-value");

		HttpHeaders result = HeaderUtils.sanitize(request, List.of("x-internal"), List.of("x-request-only"));

		assertThat(result.getFirst("x-trace-id")).isEqualTo("trace-123");
		assertThat(result.headerNames()).doesNotContain("authorization", "host", "x-internal", "x-request-only");
	}

	@Test
	void sanitizePreservesAllowedHeadersAndTheirMultipleValues() {
		HttpHeaders request = new HttpHeaders();
		request.add("X-Values", "first");
		request.add("X-Values", "second");

		HttpHeaders result = HeaderUtils.sanitize(request);

		assertThat(result.get("x-values")).isEqualTo(List.of("first", "second"));
	}

	@Test
	void fromHttpConvertsSingleAndMultipleValuesAndNormalizesContentType() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Trace-Id", "trace-123");
		headers.add("X-Values", "first");
		headers.add("X-Values", "second");
		headers.setContentType(org.springframework.http.MediaType.TEXT_PLAIN);

		MessageHeaders result = HeaderUtils.fromHttp(headers);

		assertThat(result.get("x-trace-id")).isEqualTo("trace-123");
		assertThat(result.get("x-values")).isEqualTo(List.of("first", "second"));
		assertThat(result.get(MessageHeaders.CONTENT_TYPE)).isEqualTo("text/plain");
		assertThat(result.get("content-type")).isNull();
	}

	@Test
	void fromHttpPreservesValuesAndNormalizesNames() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Count", "7");

		MessageHeaders result = HeaderUtils.fromHttp(headers);

		assertThat(result.get("x-count")).isEqualTo("7");
	}

}
