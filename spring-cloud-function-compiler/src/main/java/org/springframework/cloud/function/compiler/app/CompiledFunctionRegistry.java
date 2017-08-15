/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.function.compiler.app;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.cloud.function.compiler.AbstractFunctionCompiler;
import org.springframework.cloud.function.compiler.CompiledFunctionFactory;
import org.springframework.cloud.function.compiler.ConsumerCompiler;
import org.springframework.cloud.function.compiler.FunctionCompiler;
import org.springframework.cloud.function.compiler.SupplierCompiler;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;

import reactor.core.publisher.Flux;

/**
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 */
public class CompiledFunctionRegistry {

	private static final String SUPPLIER_DIRECTORY = "suppliers";

	private static final String FUNCTION_DIRECTORY = "functions";

	private static final String CONSUMER_DIRECTORY = "consumers";

	private final File supplierDirectory;

	private final File functionDirectory;

	private final File consumerDirectory;

	private final AbstractFunctionCompiler<Supplier<Flux<?>>> supplierCompiler = new SupplierCompiler<>();

	private final AbstractFunctionCompiler<Function<Flux<?>, Flux<?>>> functionCompiler = new FunctionCompiler<>();

	private final AbstractFunctionCompiler<Consumer<Flux<?>>> consumerCompiler = new ConsumerCompiler<>();

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
		this.doRegister(this.supplierCompiler, this.supplierDirectory, name, supplier, type);
	}

	public void registerFunction(String name, String function, String... types) {
		this.doRegister(this.functionCompiler, this.functionDirectory, name, function, types);
	}

	public void registerConsumer(String name, String consumer, String type) {
		this.doRegister(this.consumerCompiler, this.consumerDirectory, name, consumer, type);
	}

	private void doRegister(AbstractFunctionCompiler<?> compiler, File componentDirectory, String name, String functionalComponent, String... types){
		CompiledFunctionFactory<?> factory = compiler.compile(name, functionalComponent, types);
		File file = new File(componentDirectory, fileName(name));
		try {
			FileCopyUtils.copy(factory.getGeneratedClassBytes(), file);
		}
		catch (IOException e) {
			throw new IllegalArgumentException(String.format("failed to register Functional component: %s", name), e);
		}
	}

	private String fileName(String functionName) {
		return String.format("%s.%s", functionName, "fun");
	}
}
