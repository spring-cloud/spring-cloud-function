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
import org.junit.rules.ExternalResource;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.function.adapter.gcloud.FunctionInvoker;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test rule for starting the Cloud Function server.
 *
 * @author Daniel Zou
 * @author Mike Eltsufin
 */
public class CloudFunctionServer2 extends ExternalResource {

	private static final Gson gson = new Gson();

	private final Class<?> springApplicationMainClass;

	private int port = 7878;

	private ServerProcess process;

	private String function;

	/**
	 * Initializes the Cloud Function Server rule.
	 * @param springApplicationMainClass the Spring main class containing function beans
	 */
	public CloudFunctionServer2(Class<?> springApplicationMainClass, String function) {
		this.springApplicationMainClass = springApplicationMainClass;
		this.function = function;
	}

	/**
	 * Starts up the Cloud Function Server.
	 */
	@Override
	protected void before() throws InterruptedException, IOException {
	//	List extraArgs = Arrays.asList("-Dadfasdf=" + springApplicationMainClass.getCanonicalName());
		Map<String, String> environment = new HashMap<>();
		environment.put("MAIN_CLASS", springApplicationMainClass.getCanonicalName());
		if (function != null)
			environment.put("spring.cloud.function.definition", this.function);

		this.process = startServer("http", FunctionInvoker.class.getCanonicalName(), new ArrayList<>(), environment);
	}

	@Override
	protected void after() {
		process.process.destroy();
	}

	public int getPort() {
		return port;
	}

	public <I, O> void test(I request, O expectedResponse) {
		TestRestTemplate testRestTemplate = new TestRestTemplate();

		HttpHeaders headers = new HttpHeaders();
		// headers.set("spring.function", function);

		ResponseEntity<String> response = testRestTemplate.postForEntity("http://localhost:" + getPort(),
				new HttpEntity<>(gson.toJson(request), headers), String.class);

		assertThat(response.getBody()).isEqualTo(gson.toJson(expectedResponse));
	}


	private static class ServerProcess implements AutoCloseable {
		private final Process process;
		private final Future<?> outputMonitorResult;
		private final StringBuilder output;

		ServerProcess(Process process, Future<?> outputMonitorResult, StringBuilder output) {
			this.process = process;
			this.outputMonitorResult = outputMonitorResult;
			this.output = output;
		}

		Process process() {
			return process;
		}

		Future<?> outputMonitorResult() {
			return outputMonitorResult;
		}

		String output() {
			synchronized (output) {
				return output.toString();
			}
		}

		@Override
		public void close() {
			process().destroy();
		}
	}

	private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
	private static final String SERVER_READY_STRING = "Started ServerConnector";

	private ServerProcess startServer(
			String signatureType, String target, List<String> extraArgs, Map<String, String> extraEnv)
			throws IOException, InterruptedException {
		File javaHome = new File(System.getProperty("java.home"));
		assertThat(javaHome.exists()).isTrue();
		File javaBin = new File(javaHome, "bin");
		File javaCommand = new File(javaBin, "java");
		assertThat(javaCommand.exists()).isTrue();
		String myClassPath = System.getProperty("java.class.path");
		assertThat(myClassPath).isNotNull();

		List<String> command = new ArrayList<>();
		command.addAll(Arrays.asList(
				javaCommand.toString(), "-classpath", myClassPath, Invoker.class.getName()));
		command.addAll(extraArgs);

		System.out.println(command);

		ProcessBuilder processBuilder = new ProcessBuilder()
				.command(command)
				.redirectErrorStream(true);
		Map<String, String> environment = new HashMap<>();
		environment.put("PORT", String.valueOf(port));
		environment.put("K_SERVICE", "test-function");
		environment.put("FUNCTION_SIGNATURE_TYPE", signatureType) ;
		environment.put("FUNCTION_TARGET", target);
		environment.putAll(extraEnv);
		processBuilder.environment().putAll(environment);
		Process serverProcess = processBuilder.start();
		CountDownLatch ready = new CountDownLatch(1);
		StringBuilder output = new StringBuilder();
		Future<?> outputMonitorResult = EXECUTOR.submit(
				() -> monitorOutput(serverProcess.getInputStream(), ready, output));
		boolean serverReady = ready.await(5, TimeUnit.SECONDS);
		if (!serverReady) {
			serverProcess.destroy();
			throw new AssertionError("Server never became ready");
		}
		return new ServerProcess(serverProcess, outputMonitorResult, output);
	}

	private void monitorOutput(
			InputStream processOutput, CountDownLatch ready, StringBuilder output) {
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
		} catch (IOException e) {
			e.printStackTrace();
			throw new UncheckedIOException(e);
		}
	}
}
