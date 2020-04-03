/*
 * Copyright 2012-2020 the original author or authors.
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

import java.util.concurrent.TimeUnit;

import com.google.cloud.functions.invoker.runner.Invoker;
import com.google.gson.Gson;
import org.junit.rules.ExternalResource;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Test rule for starting the Cloud Function server.
 *
 * @author Daniel Zou
 * @author Mike Eltsufin
 */
public class CloudFunctionServer extends ExternalResource {

	private static final Gson gson = new Gson();

	private static int nextPort = 7777;

	private final Class<?> adapterClass;

	private final Class<?> springApplicationMainClass;

	private Thread serverThread = null;

	private int port;

	/**
	 * Initializes the Cloud Function Server rule.
	 * @param adapterClass the Cloud Function adapter class being used
	 * @param springApplicationMainClass the Spring main class containing function beans
	 */
	public CloudFunctionServer(Class<?> adapterClass, Class<?> springApplicationMainClass) {
		this.adapterClass = adapterClass;
		this.springApplicationMainClass = springApplicationMainClass;
	}

	/**
	 * Starts up the Cloud Function Server.
	 */
	@Override
	protected void before() throws InterruptedException {
		// Spring uses the System property to detect the correct main class.
		System.setProperty("MAIN_CLASS", springApplicationMainClass.getCanonicalName());

		this.port = nextPort;

		Runnable startServer = () -> {
			Invoker invoker = new Invoker(port, adapterClass.getCanonicalName(), null,
					springApplicationMainClass.getClassLoader());

			try {
				invoker.startServer();
			}
			catch (Exception e) {
				// InterruptedException means the server is shutting down
				// at the end of the test (via CTRL+C), so ignore those.
				if (!(e instanceof InterruptedException)) {
					throw new RuntimeException("Failed to start Cloud Functions Server", e);
				}
			}
		};

		this.serverThread = new Thread(startServer);
		this.serverThread.start();

		CloudFunctionServer.nextPort++;

		// Wait for the server to start up.
		TestRestTemplate template = new TestRestTemplate();
		await().atMost(5, TimeUnit.SECONDS)
				.until(() -> template.postForEntity("http://localhost:" + this.port, "test", String.class)
						.getStatusCode().is2xxSuccessful());
		// Thread.sleep(1000);
	}

	@Override
	protected void after() {

	}

	public int getPort() {
		return port;
	}

	public <I, O> void test(String function, I request, O expectedResponse) {
		TestRestTemplate testRestTemplate = new TestRestTemplate();

		HttpHeaders headers = new HttpHeaders();
		headers.set("spring.function", function);

		ResponseEntity<String> response = testRestTemplate.postForEntity("http://localhost:" + getPort(),
				new HttpEntity<>(gson.toJson(request), headers), String.class);

		assertThat(response.getBody()).isEqualTo(gson.toJson(expectedResponse));
	}

}
