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

import java.util.function.Function;

import org.springframework.cloud.function.compiler.FunctionCompiler;
import org.springframework.util.Assert;

/**
 * @author Mark Fisher
 */
public abstract class FunctionRegistrySupport implements FunctionRegistry {

	private final FunctionCompiler compiler = new FunctionCompiler();

	@Override
	public void register(String name, String code) {
		Function<?, ?> function = compiler.compile(code).getFunction();
		this.register(name, function);
	}

	@SuppressWarnings("unchecked")
	public void compose(String name, Function<?, ?>... functions) {
		Assert.isTrue(functions != null && functions.length > 1, "more than one Function is required");
		@SuppressWarnings("rawtypes")
		Function function = functions[0];
		for (int i = 1; i < functions.length; i++) {
			function = function.andThen(functions[i]);
		}
		this.register(name, function);
	}

	@SuppressWarnings("unchecked")
	public void compose(String composedFunctionName, String... functionNames) {
		Assert.isTrue(functionNames != null && functionNames.length > 1, "more than one Function is required");
		@SuppressWarnings("rawtypes")
		Function function = this.lookup(functionNames[0]);
		for (int i = 1; i < functionNames.length; i++) {
			function = function.andThen(this.lookup(functionNames[i]));
		}
		this.register(composedFunctionName, function);
	}
}
