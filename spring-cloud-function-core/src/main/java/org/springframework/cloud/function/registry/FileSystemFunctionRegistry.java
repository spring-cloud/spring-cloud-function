/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.cloud.function.registry;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.cloud.function.compiler.CompiledFunctionFactory;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;

/**
 * @author Mark Fisher
 */
public class FileSystemFunctionRegistry extends AbstractFunctionRegistry {

	private static final String CONSUMER_DIRECTORY = "consumers";

	private static final String FUNCTION_DIRECTORY = "functions";

	private static final String SUPPLIER_DIRECTORY = "suppliers";

	private final File consumerDirectory;

	private final File functionDirectory;

	private final File supplierDirectory;

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
		this.consumerDirectory = new File(directory, CONSUMER_DIRECTORY);
		this.functionDirectory = new File(directory, FUNCTION_DIRECTORY);
		this.supplierDirectory = new File(directory, SUPPLIER_DIRECTORY);
		this.consumerDirectory.mkdir();
		this.functionDirectory.mkdir();
		this.supplierDirectory.mkdir();
	}

	@Override
	public <T> Consumer<T> doLookupConsumer(String name) {
		try {
			byte[] bytes = FileCopyUtils.copyToByteArray(new File(this.consumerDirectory, fileName(name)));
			return this.deserializeConsumer(name, bytes);
		}
		catch (IOException e) {
			throw new IllegalArgumentException(String.format("failed to lookup Consumer: %s", name), e); 
		}
	}

	@Override
	public <T, R> Function<T, R> doLookupFunction(String name) {
		try {
			byte[] bytes = FileCopyUtils.copyToByteArray(new File(this.functionDirectory, fileName(name)));
			return this.deserializeFunction(name, bytes);
		}
		catch (IOException e) {
			throw new IllegalArgumentException(String.format("failed to lookup Function: %s", name), e); 
		}
	}

	@Override
	public <T> Supplier<T> doLookupSupplier(String name) {
		try {
			byte[] bytes = FileCopyUtils.copyToByteArray(new File(this.supplierDirectory, fileName(name)));
			return this.deserializeSupplier(name, bytes);
		}
		catch (IOException e) {
			throw new IllegalArgumentException(String.format("failed to lookup Supplier: %s", name), e); 
		}
	}

	@Override
	public void registerConsumer(String name, String consumer) {
		CompiledFunctionFactory<?> factory = this.compileConsumer(name, consumer);
		File file = new File(this.consumerDirectory, fileName(name));
		try {
			FileCopyUtils.copy(factory.getGeneratedClassBytes(), file);
		}
		catch (IOException e) {
			throw new IllegalArgumentException(String.format("failed to register Consumer: %s", name), e);
		}
	}

	@Override
	public void registerFunction(String name, String function) {
		CompiledFunctionFactory<?> factory = this.compileFunction(name, function);
		File file = new File(this.functionDirectory, fileName(name));
		try {
			FileCopyUtils.copy(factory.getGeneratedClassBytes(), file);
		}
		catch (IOException e) {
			throw new IllegalArgumentException(String.format("failed to register Function: %s", name), e);
		}
	}

	@Override
	public void registerSupplier(String name, String supplier) {
		CompiledFunctionFactory<?> factory = this.compileSupplier(name, supplier);
		File file = new File(this.supplierDirectory, fileName(name));
		try {
			FileCopyUtils.copy(factory.getGeneratedClassBytes(), file);
		}
		catch (IOException e) {
			throw new IllegalArgumentException(String.format("failed to register Supplier: %s", name), e);
		}
	}

	private static String fileName(String functionName) {
		return String.format("%s.%s", functionName, "fun");
	}
}
