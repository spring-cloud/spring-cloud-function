/*
 * Copyright 2018-present the original author or authors.
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.cloud.functions.HttpFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.loader.launch.Launcher;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests verifying that {@link GcfJarLauncher} registers the {@code nested:}
 * URL protocol handler before creating its classloader (GH-1336).
 * <p>
 * The test builds a Spring Boot fat JAR, then forks a subprocess that loads
 * {@code GcfJarLauncher} from that JAR through a child {@code URLClassLoader}
 * - mirroring how the Google Cloud Functions framework loads the deployed
 * function JAR. In that setup spring-boot-loader is only visible inside the
 * fat JAR, so the launcher must install a {@code nested:} handler that the
 * JVM can resolve without the loader classes being on the system classpath.
 * The subprocess is also checked to fail on the {@code nested:} protocol
 * before the launcher is constructed, proving the handler is not already
 * registered by anything else.
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

	private static final String GCF_LAUNCHER_CLASS_NAME = "org.springframework.cloud.function.adapter.gcp.GcfJarLauncher";

	private static final String FUNCTION_INVOKER_CLASS_NAME = "org.springframework.cloud.function.adapter.gcp.FunctionInvoker";

	@TempDir
	Path tempDir;

	@Test
	public void nestedProtocolIsRegisteredByGcfJarLauncher() throws Exception {
		String javaHome = System.getProperty("java.home");
		Path classesDir = tempDir.resolve("classes");
		compile(javaHome, classesDir);

		Path functionJar = createFunctionJar(classesDir);
		Path frameworkApiJar = codeSource(HttpFunction.class);

		Process process = new ProcessBuilder(Path.of(javaHome, "bin", "java").toString(), "-cp",
				String.join(File.pathSeparator, functionJar.toString(), frameworkApiJar.toString(),
						classesDir.toString()),
				"ProtocolCheck", functionJar.toString())
			.redirectErrorStream(true)
			.start();
		boolean finished = process.waitFor(2, TimeUnit.MINUTES);
		String output = new BufferedReader(new InputStreamReader(process.getInputStream())).lines()
			.collect(Collectors.joining("\n"));
		if (!finished) {
			process.destroyForcibly();
		}

		assertThat(finished).as("ProtocolCheck timed out:\n" + output).isTrue();
		assertThat(process.exitValue()).as("ProtocolCheck exit code:\n" + output).isZero();
		assertThat(output).as("ProtocolCheck output")
			.contains(PROTOCOL_NOT_REGISTERED + ": unknown protocol: nested")
			.contains(GCF_JAR_LAUNCHER_SUCCEEDED)
			.contains(PROTOCOL_NOW_REGISTERED);
	}

	private void compile(String javaHome, Path classesDir) throws Exception {
		Files.createDirectories(classesDir);
		Path protocolCheck = tempDir.resolve("ProtocolCheck.java");
		writeResource(protocolCheck, "/ProtocolCheck.java");
		Path stubInvoker = tempDir.resolve("FunctionInvoker.java");
		writeResource(stubInvoker, "/StubFunctionInvoker.java");
		Process compile = new ProcessBuilder(Path.of(javaHome, "bin", "javac").toString(), "-d",
				classesDir.toString(), protocolCheck.toString(), stubInvoker.toString())
			.redirectErrorStream(true)
			.start();
		String compileOutput = new BufferedReader(new InputStreamReader(compile.getInputStream())).lines()
			.collect(Collectors.joining("\n"));
		boolean finished = compile.waitFor(1, TimeUnit.MINUTES);
		if (!finished) {
			compile.destroyForcibly();
		}
		assertThat(finished).as("javac timed out").isTrue();
		assertThat(compile.exitValue()).as("compilation failed:\n" + compileOutput).isZero();
	}

	private Path createFunctionJar(Path classesDir) throws Exception {
		Path functionJar = tempDir.resolve("function.jar");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(functionJar))) {
			writeEntry(zip, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
			Path loaderJar = codeSource(Launcher.class);
			try (JarFile loader = new JarFile(loaderJar.toFile())) {
				Enumeration<JarEntry> entries = loader.entries();
				while (entries.hasMoreElements()) {
					JarEntry entry = entries.nextElement();
					if (!entry.isDirectory() && !entry.getName().startsWith("META-INF/")) {
						writeEntry(zip, entry.getName(), loader.getInputStream(entry).readAllBytes());
					}
				}
			}
			writeLauncherClasses(zip);
			writeEntry(zip, "BOOT-INF/lib/spring-cloud-function-adapter-gcp.jar", nestedAdapterJar(classesDir));
		}
		return functionJar;
	}

	private void writeLauncherClasses(ZipOutputStream zip) throws Exception {
		Path adapterClasses = codeSource(GcfJarLauncher.class);
		if (!Files.isDirectory(adapterClasses)) {
			throw new IllegalStateException("Adapter classes are not on the classpath as a directory: " + adapterClasses);
		}
		String launcherPrefix = GCF_LAUNCHER_CLASS_NAME.replace('.', '/');
		try (Stream<Path> paths = Files.walk(adapterClasses)) {
			for (Path path : paths.toList()) {
				if (!Files.isRegularFile(path)) {
					continue;
				}
				String entryName = adapterClasses.relativize(path).toString().replace(File.separatorChar, '/');
				if (entryName.startsWith(launcherPrefix) && entryName.endsWith(".class")) {
					writeEntry(zip, entryName, Files.readAllBytes(path));
				}
			}
		}
	}

	private byte[] nestedAdapterJar(Path classesDir) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			writeEntry(zip, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
			Path stub = classesDir.resolve(FUNCTION_INVOKER_CLASS_NAME.replace('.', '/') + ".class");
			writeEntry(zip, FUNCTION_INVOKER_CLASS_NAME.replace('.', '/') + ".class", Files.readAllBytes(stub));
		}
		return bytes.toByteArray();
	}

	private static void writeResource(Path target, String resource) throws IOException {
		try (InputStream in = Objects.requireNonNull(GcfJarLauncherTests.class.getResourceAsStream(resource),
				"Resource " + resource + " not found")) {
			Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static Path codeSource(Class<?> type) throws Exception {
		return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
	}

	private static void writeEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(content);
		zip.closeEntry();
	}

}
