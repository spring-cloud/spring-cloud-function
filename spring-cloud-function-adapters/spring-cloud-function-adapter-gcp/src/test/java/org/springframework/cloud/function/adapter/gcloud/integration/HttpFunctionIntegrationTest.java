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

package org.springframework.cloud.function.adapter.gcloud.integration;

import org.junit.BeforeClass;
import org.junit.Test;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.function.adapter.gcloud.GcfSpringBootHttpRequestHandler;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for GCF Http Functions.
 *
 * @author Daniel Zou
 */
public class HttpFunctionIntegrationTest {

	// Port to start the Cloud Function Server
	private static final int PORT = 7777;

	// The Cloud Functions adapter class to serve
	private static final Class<?> ADAPTER_CLASS = GcfSpringBootHttpRequestHandler.class;

	// The main class which define the Spring Cloud Functions
	private static final Class<?> MAIN_CLASS = CloudFunctionMain.class;

	@BeforeClass
	public static void setup() {
		CloudFunctionServerUtils.startServer(PORT, ADAPTER_CLASS, MAIN_CLASS);
	}

	@Test
	public void test() {
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		ResponseEntity<String> response = testRestTemplate.postForEntity("http://localhost:" + PORT, "hello",
				String.class);
		assertThat(response.getBody()).isEqualTo("\"HELLO\"");
	}
}
