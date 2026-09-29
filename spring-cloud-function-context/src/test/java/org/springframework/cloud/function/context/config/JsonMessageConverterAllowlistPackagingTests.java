/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.cloud.function.context.config;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonMessageConverterAllowlistPackagingTests {

	private static final String ALLOWLIST = "META-INF/deserializable.types";

	@Test
	public void readsAllowlistFromDirectoryOnClasspath(@TempDir Path temp) throws Exception {
		Path directory = temp.resolve("classes");
		Path metaInf = Files.createDirectories(directory.resolve("META-INF"));
		Files.writeString(metaInf.resolve("deserializable.types"), Person.class.getName());

		assertThat(convertWithAllowlistAt(directory.toUri().toURL())).isInstanceOf(Person.class);
	}

	@Test
	public void readsAllowlistNestedInsideJarOnClasspath(@TempDir Path temp) throws Exception {
		URL jar = jarContainingAllowlist(temp.resolve("allowlist.jar"), Person.class.getName());

		assertThat(allowlistLoader(jar).getResource(ALLOWLIST))
				.hasToString("jar:" + jar + "!/META-INF/deserializable.types");

		assertThat(convertWithAllowlistAt(jar))
				.as("an allowlist nested inside an archive must be read, as it is when an application "
						+ "is packaged as a Spring Boot fat jar")
				.isInstanceOf(Person.class);
	}

	@Test
	public void ignoresTypeParameterWhenNestedAllowlistDoesNotNameIt(@TempDir Path temp) throws Exception {
		URL jar = jarContainingAllowlist(temp.resolve("allowlist.jar"), "com.example.SomethingElse");

		assertThat(convertWithAllowlistAt(jar)).isNotInstanceOf(Person.class);
	}

	private Object convertWithAllowlistAt(URL allowlist) throws IOException {
		ClassLoader original = Thread.currentThread().getContextClassLoader();
		try (URLClassLoader loader = allowlistLoader(allowlist)) {
			Thread.currentThread().setContextClassLoader(loader);
			JsonMessageConverter converter = new JsonMessageConverter(new JacksonMapper(new ObjectMapper()));
			Message<String> message = MessageBuilder.withPayload("{\"name\":\"bill\"}")
					.setHeader(MessageHeaders.CONTENT_TYPE,
							MimeTypeUtils.APPLICATION_JSON + ";type=" + Person.class.getName())
					.build();
			return converter.convertFromInternal(message, Object.class, null);
		}
		finally {
			Thread.currentThread().setContextClassLoader(original);
		}
	}

	private URLClassLoader allowlistLoader(URL allowlist) {
		ClassLoader parent = getClass().getClassLoader();
		return new URLClassLoader(new URL[] { allowlist }, parent) {

			@Override
			public URL getResource(String name) {
				return ALLOWLIST.equals(name) ? findResource(name) : super.getResource(name);
			}

			@Override
			public Enumeration<URL> getResources(String name) throws IOException {
				return ALLOWLIST.equals(name) ? findResources(name) : super.getResources(name);
			}

		};
	}

	private URL jarContainingAllowlist(Path jar, String allowlistedType) throws IOException {
		try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
			out.putNextEntry(new JarEntry(ALLOWLIST));
			out.write(allowlistedType.getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
		}
		return jar.toUri().toURL();
	}

	public static class Person {

		private String name;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

	}

}
