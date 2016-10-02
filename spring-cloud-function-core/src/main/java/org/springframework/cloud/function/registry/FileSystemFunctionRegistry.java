/*
 * Copyright 2016 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.registry;

import java.io.File;
import java.io.IOException;
import java.util.function.Function;

import org.springframework.cloud.function.compiler.CompiledFunctionFactory;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;

/**
 * @author Mark Fisher
 */
public class FileSystemFunctionRegistry extends AbstractFunctionRegistry {

	private final File directory;

	public FileSystemFunctionRegistry() {
		this(new File("/tmp/function-registry"));
	}

	public FileSystemFunctionRegistry(File directory) {
		Assert.notNull(directory, "Directory must not be null");
		if (!directory.exists()) {
			directory.mkdirs();
		}
		else {
			Assert.isTrue(directory.isDirectory(),
					String.format("%s is not a directory.", directory.getAbsolutePath()));
		}
		this.directory = directory;
	}

	@Override
	public <T, R> Function<T, R> doLookup(String name) {
		try {
			byte[] bytes = FileCopyUtils.copyToByteArray(new File(this.directory, fileName(name)));
			return this.deserialize(name, bytes);
		}
		catch (IOException e) {
			throw new IllegalArgumentException(String.format("failed to lookup function: %s", name), e); 
		}
	}

	public void register(String name, String function) {
		CompiledFunctionFactory<?, ?> factory = this.compile(name, function);
		File file = new File(this.directory, fileName(name));
		try {
			FileCopyUtils.copy(factory.getGeneratedClassBytes(), file);
		}
		catch (IOException e) {
			throw new IllegalArgumentException(String.format("failed to register function: %s", name), e);
		}
	}

	private static String fileName(String functionName) {
		return String.format("%s.%s", functionName, "fun");
	}
}
