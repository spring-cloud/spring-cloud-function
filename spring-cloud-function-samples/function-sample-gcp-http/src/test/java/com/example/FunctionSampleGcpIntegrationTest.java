/*
 * Copyright 2020-2025 the original author or authors.
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

package com.example;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import org.springframework.boot.web.server.test.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionSampleGcpIntegrationTest {

	private TestRestTemplate rest = new TestRestTemplate();

	//@Test
	public void testSample() throws IOException, InterruptedException {
		try (LocalServerTestSupport.ServerProcess process = LocalServerTestSupport.startServer(CloudFunctionMain.class)) {
			String result = rest.postForObject("http://localhost:8080/", "Hello", String.class);
			assertThat(result).isEqualTo("\"HELLO\"");
		}
	}
}
