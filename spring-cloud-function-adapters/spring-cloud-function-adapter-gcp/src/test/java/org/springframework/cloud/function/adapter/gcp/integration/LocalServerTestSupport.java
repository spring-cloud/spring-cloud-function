/*
 * Copyright 2020-present the original author or authors.
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.google.cloud.functions.invoker.runner.Invoker;
import com.google.gson.Gson;


import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.cloud.function.adapter.gcp.FunctionInvoker;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test support class for running tests on the local Cloud Function server.
 *
 * @author Daniel Zou
 * @author Mike Eltsufin
 * @author Chris Bono
 */
@AutoConfigureTestRestTemplate
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

	static ServerProcess startServer(Class<?> springApplicationMainClass, String function) throws IOException {
		String signatureType = "http";
		String target = FunctionInvoker.class.getCanonicalName();

		File javaHome = new File(System.getProperty("java.home"));
		assertThat(javaHome.exists()).isTrue();
		File javaBin = new File(javaHome, "bin");
		File javaCommand = new File(javaBin, "java.exe");
		if (!javaCommand.exists()) {
			javaCommand = new File(javaBin, "java");
		}
		assertThat(javaCommand.exists()).isTrue();
		String myClassPath = System.getProperty("java.class.path");
		assertThat(myClassPath).isNotNull();

		List<String> command = new ArrayList<>();
		command.addAll(Arrays.asList(javaCommand.toString(), "-classpath", myClassPath, Invoker.class.getName()));

		ProcessBuilder processBuilder = new ProcessBuilder().command(command).redirectErrorStream(true);
		Map<String, String> environment = new HashMap<>();
		environment.put("PORT", String.valueOf(0));
		environment.put("K_SERVICE", "test-function");
		environment.put("FUNCTION_SIGNATURE_TYPE", signatureType);
		environment.put("FUNCTION_TARGET", target);
		environment.put("MAIN_CLASS", springApplicationMainClass.getCanonicalName());
		if (function != null) {
			environment.put("spring.cloud.function.definition", function);
		}
		processBuilder.environment().putAll(environment);
		Process serverProcess = processBuilder.start();
		Future<Integer> outputMonitorResult = EXECUTOR.submit(() -> monitorOutput(serverProcess.getInputStream()));

		int port;
		try {
			port = outputMonitorResult.get(5L, TimeUnit.SECONDS);
		}
		catch (Exception ex) {
			serverProcess.destroy();
			throw new AssertionError("Server never became ready");
		}
		return new ServerProcess(serverProcess, port);
	}

	private static Integer monitorOutput(InputStream processOutput) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(processOutput))) {
			String line;
			while ((line = reader.readLine()) != null) {
				System.out.println(line);
				if (line.contains(SERVER_READY_STRING)) {
					// Started ServerConnector@192b07fd{HTTP/1.1,[http/1.1]}{0.0.0.0:59259}
					String portStr = line.substring(line.lastIndexOf(':') + 1, line.lastIndexOf('}'));
					return Integer.parseInt(portStr);
				}
				if (line.contains("WARNING")) {
					throw new AssertionError("Found warning in server output:\n" + line);
				}
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		throw new RuntimeException("End of input stream and server never became ready");
	}

	static class ServerProcess implements AutoCloseable {

		private final Process process;

		private final int port;

		ServerProcess(Process process, int port) {
			this.process = process;
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
