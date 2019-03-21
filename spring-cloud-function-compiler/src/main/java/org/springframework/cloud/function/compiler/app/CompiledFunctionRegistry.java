/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.function.compiler.app;

import java.io.File;
import java.io.IOException;

import org.springframework.cloud.function.compiler.CompiledFunctionFactory;
import org.springframework.cloud.function.compiler.ConsumerCompiler;
import org.springframework.cloud.function.compiler.FunctionCompiler;
import org.springframework.cloud.function.compiler.SupplierCompiler;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;

import reactor.core.publisher.Flux;

/**
 * @author Mark Fisher
 */
public class CompiledFunctionRegistry {

	private static final String SUPPLIER_DIRECTORY = "suppliers";

	private static final String FUNCTION_DIRECTORY = "functions";

	private static final String CONSUMER_DIRECTORY = "consumers";

	private final File supplierDirectory;

	private final File functionDirectory;

	private final File consumerDirectory;

	private final SupplierCompiler<Flux<?>> supplierCompiler = new SupplierCompiler<>();

	private final FunctionCompiler<Flux<?>, Flux<?>> functionCompiler = new FunctionCompiler<>();

	private final ConsumerCompiler<?> consumerCompiler = new ConsumerCompiler<>();

	public CompiledFunctionRegistry() {
		this(new File("/tmp/function-registry"));
	}

	public CompiledFunctionRegistry(File directory) {
		Assert.notNull(directory, "Directory must not be null");
		if (!directory.exists()) {
			directory.mkdirs();
		}
		else {
			Assert.isTrue(directory.isDirectory(),
					String.format("%s is not a directory.", directory.getAbsolutePath()));
		}
		this.supplierDirectory = new File(directory, SUPPLIER_DIRECTORY);
		this.functionDirectory = new File(directory, FUNCTION_DIRECTORY);
		this.consumerDirectory = new File(directory, CONSUMER_DIRECTORY);
		this.supplierDirectory.mkdir();
		this.functionDirectory.mkdir();
		this.consumerDirectory.mkdir();
	}

	public void registerSupplier(String name, String supplier, String type) {
		CompiledFunctionFactory<?> factory = this.supplierCompiler.compile(name, supplier, type);
		File file = new File(this.supplierDirectory, fileName(name));
		try {
			FileCopyUtils.copy(factory.getGeneratedClassBytes(), file);
		}
		catch (IOException e) {
			throw new IllegalArgumentException(String.format("failed to register Supplier: %s", name), e);
		}
	}

	public void registerFunction(String name, String function, String... types) {
		CompiledFunctionFactory<?> factory = this.functionCompiler.compile(name, function, types);
		File file = new File(this.functionDirectory, fileName(name));
		try {
			FileCopyUtils.copy(factory.getGeneratedClassBytes(), file);
		}
		catch (IOException e) {
			throw new IllegalArgumentException(String.format("failed to register Function: %s", name), e);
		}
	}

	public void registerConsumer(String name, String consumer, String type) {
		CompiledFunctionFactory<?> factory = this.consumerCompiler.compile(name, consumer, type);
		File file = new File(this.consumerDirectory, fileName(name));
		try {
			FileCopyUtils.copy(factory.getGeneratedClassBytes(), file);
		}
		catch (IOException e) {
			throw new IllegalArgumentException(String.format("failed to register Consumer: %s", name), e);
		}
	}

	private String fileName(String functionName) {
		return String.format("%s.%s", functionName, "fun");
	}
}
