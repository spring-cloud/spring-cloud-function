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

import java.util.function.Consumer;

/**
 * @author Mark Fisher
 */
public class ConsumerCompiler<T> extends AbstractFunctionCompiler<Consumer<T>> {

	private final String inputType;

	public ConsumerCompiler() {
		this("Flux<Object>");
	}

	public ConsumerCompiler(String inputType) {
		super(ResultType.Consumer, inputType);
		this.inputType = inputType;
	}

	@Override
	protected CompiledFunctionFactory<Consumer<T>> postProcessCompiledFunctionFactory(CompiledFunctionFactory<Consumer<T>> factory) {
		factory.setInputType(this.inputType);
		return factory;
	}
}
