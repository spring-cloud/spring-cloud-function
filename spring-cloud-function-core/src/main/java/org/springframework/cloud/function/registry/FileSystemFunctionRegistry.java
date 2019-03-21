/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.registry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.function.Function;

import org.springframework.util.Assert;

/**
 * @author Mark Fisher
 */
public class FileSystemFunctionRegistry extends FunctionRegistrySupport {

	private final File directory;

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
	public Function<?, ?> lookup(String name) {
		File file = new File(this.directory, name);
		Assert.isTrue(file.exists(), String.format("File '%s' does not exist within the registry's directory.", name));
		try {
			ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(file));
			return (Function<?, ?>) inputStream.readObject();
		}
		catch (Exception e) {
			throw new RuntimeException("failed to lookup Function", e);
		}
	}

	@Override
	public void register(String name, Function<?, ?> function) {
		File file = new File(this.directory, name);
		try {
			ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(file));
			outputStream.writeObject(function);
		}
		catch (IOException e) {
			throw new RuntimeException("failed to register Function", e);
		}
	}
}
