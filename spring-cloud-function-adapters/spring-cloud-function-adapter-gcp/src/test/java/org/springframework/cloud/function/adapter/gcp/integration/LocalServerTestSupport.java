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

package org.springframework.cloud.function.adapter.gcp.integration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.google.cloud.functions.invoker.runner.Invoker;
import com.google.gson.Gson;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.function.adapter.gcp.FunctionInvoker;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test support class for running tests on the local Cloud Function server.
 *
 * @author Daniel Zou
 * @author Mike Eltsufin
 */
final public class LocalServerTestSupport {

	private static final Gson gson = new Gson();

	private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

	private static final String SERVER_READY_STRING = "Started ServerConnector";

	private LocalServerTestSupport() {
	}

	/**
	 * Starts up the Cloud Function Server and executes the test.
	 */
	public static <I, O> void verify(Class<?> mainClass, String function, I input, O expectedOutput) {
		try (ServerProcess serverProcess = LocalServerTestSupport.startServer(mainClass, function)) {
			TestRestTemplate testRestTemplate = new TestRestTemplate();

			HttpHeaders headers = new HttpHeaders();

			ResponseEntity<String> response = testRestTemplate.postForEntity(
					"http://localhost:" + serverProcess.getPort(), new HttpEntity<>(gson.toJson(input), headers),
					String.class);

			assertThat(response.getBody()).isEqualTo(gson.toJson(expectedOutput));
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	static ServerProcess startServer(Class<?> springApplicationMainClass, String function)
			throws InterruptedException, IOException {
		int port = SocketUtils.findAvailableTcpPort();

		String signatureType = "http";
		String target = FunctionInvoker.class.getCanonicalName();

		File javaHome = new File(System.getProperty("java.home"));
		assertThat(javaHome.exists()).isTrue();
		File javaBin = new File(javaHome, "bin");
		File javaCommand = new File(javaBin, "java");
		assertThat(javaCommand.exists()).isTrue();
		String myClassPath = System.getProperty("java.class.path");
		assertThat(myClassPath).isNotNull();

		List<String> command = new ArrayList<>();
		command.addAll(Arrays.asList(javaCommand.toString(), "-classpath", myClassPath, Invoker.class.getName()));

		ProcessBuilder processBuilder = new ProcessBuilder().command(command).redirectErrorStream(true);
		Map<String, String> environment = new HashMap<>();
		environment.put("PORT", String.valueOf(port));
		environment.put("K_SERVICE", "test-function");
		environment.put("FUNCTION_SIGNATURE_TYPE", signatureType);
		environment.put("FUNCTION_TARGET", target);
		environment.put("MAIN_CLASS", springApplicationMainClass.getCanonicalName());
		if (function != null) {
			environment.put("spring.cloud.function.definition", function);
		}
		processBuilder.environment().putAll(environment);
		Process serverProcess = processBuilder.start();
		CountDownLatch ready = new CountDownLatch(1);
		StringBuilder output = new StringBuilder();
		Future<?> outputMonitorResult = EXECUTOR
				.submit(() -> monitorOutput(serverProcess.getInputStream(), ready, output));
		boolean serverReady = ready.await(5, TimeUnit.SECONDS);
		if (!serverReady) {
			serverProcess.destroy();
			throw new AssertionError("Server never became ready");
		}
		return new ServerProcess(serverProcess, outputMonitorResult, output, port);
	}

	private static void monitorOutput(InputStream processOutput, CountDownLatch ready, StringBuilder output) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(processOutput))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.contains(SERVER_READY_STRING)) {
					ready.countDown();
				}
				System.out.println(line);
				synchronized (output) {
					output.append(line).append('\n');
				}
				if (line.contains("WARNING")) {
					throw new AssertionError("Found warning in server output:\n" + line);
				}
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static class ServerProcess implements AutoCloseable {

		private final Process process;

		private final Future<?> outputMonitorResult;

		private final StringBuilder output;

		private final int port;

		ServerProcess(Process process, Future<?> outputMonitorResult, StringBuilder output, int port) {
			this.process = process;
			this.outputMonitorResult = outputMonitorResult;
			this.output = output;
			this.port = port;
		}

		Process process() {
			return process;
		}


		@Override
		public void close() {
			process().destroy();
		}

		public int getPort() {
			return port;
		}

	}

}
