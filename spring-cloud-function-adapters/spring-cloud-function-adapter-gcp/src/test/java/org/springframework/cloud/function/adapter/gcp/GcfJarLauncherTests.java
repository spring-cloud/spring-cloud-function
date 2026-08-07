/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.cloud.function.adapter.gcp;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests verifying that {@link GcfJarLauncher} registers the {@code nested:}
 * URL protocol handler via {@code Handlers.register()} before creating its
 * classloader. Without this, {@code JarUrlClassLoader} would fail with
 * {@code java.net.MalformedURLException: unknown protocol: nested} when
 * trying to resolve JARs inside the repackaged archive (GH-1336).
 * <p>
 * The test forks a subprocess to avoid interference from other Spring Boot
 * classes that may have already registered the protocol in the current JVM.
 *
 * @author Roman Akentev
 * @see <a href="https://github.com/spring-cloud/spring-cloud-function/issues/1336">GH-1336</a>
 */
public class GcfJarLauncherTests {

	public static final String PROTOCOL_ALREADY_REGISTERED = "PROTOCOL_ALREADY_REGISTERED";

	public static final String PROTOCOL_NOT_REGISTERED = "PROTOCOL_NOT_REGISTERED";

	public static final String GCF_JAR_LAUNCHER_SUCCEEDED = "GCF_JAR_LAUNCHER_SUCCEEDED";

	public static final String GCF_JAR_LAUNCHER_FAILED = "GCF_JAR_LAUNCHER_FAILED";

	public static final String PROTOCOL_NOW_REGISTERED = "PROTOCOL_NOW_REGISTERED";

	public static final String PROTOCOL_STILL_NOT_REGISTERED = "PROTOCOL_STILL_NOT_REGISTERED";

	@TempDir
	Path tempDir;

	@Test
	public void nestedProtocolIsRegisteredByGcfJarLauncher() throws Exception {
		String javaHome = System.getProperty("java.home");
		String classPath = System.getProperty("java.class.path");

		Path sourceFile = tempDir.resolve("ProtocolCheck.java");
		try (InputStream in = Objects.requireNonNull(
				getClass().getResourceAsStream("/ProtocolCheck.java"),
				"Resource /ProtocolCheck.java not found")) {
			Files.copy(in, sourceFile, StandardCopyOption.REPLACE_EXISTING);
		}

		Process compile = new ProcessBuilder(
				Path.of(javaHome, "bin", "javac").toString(),
				"-cp", classPath,
				"-d", tempDir.toString(),
				sourceFile.toString())
				.redirectErrorStream(true)
				.start();
		String compileOutput = new BufferedReader(
				new InputStreamReader(compile.getInputStream()))
				.lines().collect(Collectors.joining("\n"));
		int compileExit = compile.waitFor();
		assertThat(compileExit)
				.as("compilation failed:\n" + compileOutput)
				.isZero();

		Process run = new ProcessBuilder(
				Path.of(javaHome, "bin", "java").toString(),
				"-cp", classPath + File.pathSeparatorChar + tempDir,
				"ProtocolCheck")
				.redirectErrorStream(true)
				.start();
		String output = new BufferedReader(new InputStreamReader(run.getInputStream()))
				.lines().collect(Collectors.joining("\n"));
		int exitCode = run.waitFor();

		assertThat(exitCode).as("ProtocolCheck exit code").isZero();
		assertThat(output).as("ProtocolCheck output")
			.contains(PROTOCOL_NOT_REGISTERED + ": unknown protocol: nested")
			.contains(PROTOCOL_NOW_REGISTERED);
	}

}
