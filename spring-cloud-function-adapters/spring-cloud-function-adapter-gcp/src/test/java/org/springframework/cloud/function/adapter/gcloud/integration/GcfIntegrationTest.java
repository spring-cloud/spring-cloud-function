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

import com.google.cloud.functions.invoker.runner.Invoker;
import org.junit.BeforeClass;
import org.junit.Test;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.function.adapter.gcloud.GcfSpringBootHttpRequestHandler;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test simulates deploying the function locally and sending it a request.
 *
 * @author Daniel Zou
 */
public class GcfIntegrationTest {

	private static final int PORT = 7777;

	private static final String MAIN_CLASS = CloudFunctionMain.class.getCanonicalName();

	private static final String ADAPTER_CLASS = GcfSpringBootHttpRequestHandler.class.getCanonicalName();

	@BeforeClass
	public static void setup() {
		System.setProperty("MAIN_CLASS", MAIN_CLASS);
		runCloudFunctionServer();
	}

	@Test
	public void test() {
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		ResponseEntity<String> response = testRestTemplate.postForEntity("http://localhost:" + PORT, "hello",
				String.class);
		assertThat(response.getBody()).isEqualTo("\"HELLO\"");
	}

	/**
	 * Starts the Cloud Function Server in a separate thread that exists for the lifetime of
	 * the test.
	 */
	private static void runCloudFunctionServer() {
		Runnable startServer = () -> {
			Invoker invoker = new Invoker(
				PORT,
				ADAPTER_CLASS,
				null,
				CloudFunctionMain.class.getClassLoader());

			try {
				invoker.startServer();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		};

		Thread thread = new Thread(startServer);
		thread.start();
	}
}
