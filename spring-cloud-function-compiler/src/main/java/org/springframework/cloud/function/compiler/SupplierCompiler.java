/*
 * Copyright 2016-2017 the original author or authors.
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

package org.springframework.cloud.function.compiler;

import java.util.function.Supplier;

/**
 * @author Mark Fisher
 */
public class SupplierCompiler<T> extends AbstractFunctionCompiler<Supplier<T>> {

	private final String outputType;

	public SupplierCompiler() {
		this("Flux<Object>");
	}

	public SupplierCompiler(String outputType) {
		super(ResultType.Supplier, outputType);
		this.outputType = outputType;
	}

	@Override
	protected CompiledFunctionFactory<Supplier<T>> postProcessCompiledFunctionFactory(CompiledFunctionFactory<Supplier<T>> factory) {
		factory.setOutputType(this.outputType);
		return factory;
	}
}
