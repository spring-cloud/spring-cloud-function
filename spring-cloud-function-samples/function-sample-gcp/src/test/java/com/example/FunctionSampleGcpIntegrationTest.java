/*
 * Copyright 2020-2020 the original author or authors.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class FunctionSampleGcpIntegrationTest {

	private TestRestTemplate rest = new TestRestTemplate();

	private CountDownLatch startedSuccessfully = new CountDownLatch(1);

	@Test
	public void testSample() throws IOException {
		Process process = new ProcessBuilder("mvn", "function:run").start();

		try {
			Executors.defaultThreadFactory().newThread(new OutputCapture(process.getErrorStream())).start();
			Executors.defaultThreadFactory().newThread(new OutputCapture(process.getInputStream())).start();

			if (startedSuccessfully.await(10, TimeUnit.SECONDS)) {
				String result = rest.postForObject("http://localhost:8080/", "Hello", String.class);
				assertThat(result).isEqualTo("HELLO");
			}
			else {
				fail("Failed to start the function.");
			}
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
		finally {
			process.destroy();
		}
	}

	class OutputCapture implements Runnable {

		private InputStream inputStream;

		OutputCapture(InputStream inputStream) {
			this.inputStream = inputStream;
		}

		@Override
		public void run() {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
				String line;
				while ((line = reader.readLine()) != null) {
					System.out.println(line);
					if (line.equals("INFO: URL: http://localhost:8080/")) {
						startedSuccessfully.countDown();
					}
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}

	}
}
