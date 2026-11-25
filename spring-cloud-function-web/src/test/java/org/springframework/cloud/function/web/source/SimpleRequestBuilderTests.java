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

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.function.web.FunctionHttpProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SimpleRequestBuilder}.
 *
 * @author Oleg Zhurakousky
 */
class SimpleRequestBuilderTests {

	@Test
	void destinationIsNotResolvedAgainstTheEnvironment() {
		MockEnvironment environment = new MockEnvironment().withProperty("my.secret", "topsecret");
		SimpleRequestBuilder builder = new SimpleRequestBuilder(environment, new FunctionHttpProperties());
		builder.setHeaders(Map.of("X-Destination", "to-${destination}"));

		HttpHeaders headers = builder.headers("${my.secret}", "payload");

		assertThat(headers.getFirst("X-Destination")).isEqualTo("to-${my.secret}");
	}

	@Test
	void placeholdersOtherThanTheDestinationAreStillResolved() {
		MockEnvironment environment = new MockEnvironment().withProperty("my.token", "abc");
		SimpleRequestBuilder builder = new SimpleRequestBuilder(environment, new FunctionHttpProperties());
		builder.setHeaders(Map.of("X-Custom", "${my.token}-${destination}-${my.token}"));

		HttpHeaders headers = builder.headers("target", "payload");

		assertThat(headers.getFirst("X-Custom")).isEqualTo("abc-target-abc");
	}

	@Test
	void destinationPlaceholderWinsOverAPropertyNamedDestination() {
		MockEnvironment environment = new MockEnvironment().withProperty("destination", "from-environment");
		SimpleRequestBuilder builder = new SimpleRequestBuilder(environment, new FunctionHttpProperties());
		builder.setHeaders(Map.of("X-Destination", "${destination}"));

		HttpHeaders headers = builder.headers("target", "payload");

		assertThat(headers.getFirst("X-Destination")).isEqualTo("target");
	}

}
