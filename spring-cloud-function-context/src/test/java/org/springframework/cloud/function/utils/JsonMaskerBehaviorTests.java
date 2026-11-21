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

package org.springframework.cloud.function.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the observable behavior of {@link JsonMasker}.
 */
public class JsonMaskerBehaviorTests {

	private static final String MASK = "*******";

	@Test
	public void instanceIsSharedAndKeysAreLoadedSorted() {
		assertThat(JsonMasker.INSTANCE()).isSameAs(JsonMasker.INSTANCE());
		String[] keys = JsonMasker.INSTANCE().getKeysToMask();
		assertThat(keys).contains("asdf1", "SS", "eventSourceARN");
		assertThat(keys).isSorted();
	}

	@Test
	public void keysCanBeAddedAtRuntimeAndAreReturnedByTheSameInstance() {
		JsonMasker masker = JsonMasker.INSTANCE(Set.of("runtimeKey"));

		assertThat(masker).isSameAs(JsonMasker.INSTANCE());
		assertThat(Arrays.asList(masker.getKeysToMask())).contains("runtimeKey");
		assertThat(masker.mask("{\"runtimeKey\":\"secret\",\"other\":\"visible\"}"))
				.isEqualTo("{\"runtimeKey\":\"" + MASK + "\",\"other\":\"visible\"}");
	}

	@Test
	public void valuesOfConfiguredKeysAreMaskedAtEveryLevelOfMapsAndArraysOfMaps() {
		String json = "{\"asdf1\":\"a\",\"keep\":{\"asdf1\":\"b\",\"list\":[{\"asdf1\":\"c\",\"x\":1}]}}";

		assertThat(JsonMasker.INSTANCE().mask(json)).isEqualTo(
				"{\"asdf1\":\"" + MASK + "\",\"keep\":{\"asdf1\":\"" + MASK + "\",\"list\":[{\"asdf1\":\"" + MASK
						+ "\",\"x\":1}]}}");
	}

	@Test
	public void topLevelArrayOfObjectsIsMasked() {
		assertThat(JsonMasker.INSTANCE().mask("[{\"asdf1\":\"a\"},{\"keep\":\"b\"}]"))
				.isEqualTo("[{\"asdf1\":\"" + MASK + "\"},{\"keep\":\"b\"}]");
	}

	@Test
	public void maskingByteArrayJsonReturnsMaskedString() {
		byte[] json = "{\"asdf1\":\"a\"}".getBytes(StandardCharsets.UTF_8);

		assertThat(JsonMasker.INSTANCE().mask(json)).isEqualTo("{\"asdf1\":\"" + MASK + "\"}");
	}

	@Test
	public void nonJsonStringIsReturnedUnchanged() {
		assertThat(JsonMasker.INSTANCE().mask("just text")).isEqualTo("just text");
	}

	@Test
	public void arraysNestedInArraysAreMasked() {
		assertThat(JsonMasker.INSTANCE().mask("{\"list\":[[{\"asdf1\":\"x\"}]]}"))
				.isEqualTo("{\"list\":[[{\"asdf1\":\"" + MASK + "\"}]]}");
	}

	@Test
	public void nonJsonByteArrayIsReturnedAsText() {
		assertThat(JsonMasker.INSTANCE().mask("just text".getBytes(StandardCharsets.UTF_8))).isEqualTo("just text");
	}

	@Test
	public void keysAreLoadedFromResourcesPackagedInJars(@TempDir Path dir) throws IOException {
		Path jar = dir.resolve("keys.jar");
		try (OutputStream out = Files.newOutputStream(jar); JarOutputStream jos = new JarOutputStream(out)) {
			jos.putNextEntry(new JarEntry("META-INF/mask.keys"));
			jos.write("first, second\n\nthird\n".getBytes(StandardCharsets.UTF_8));
			jos.closeEntry();
		}
		try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
			assertThat(JsonMasker.loadKeys(loader)).containsExactly("first", "second", "third");
		}
	}

}
