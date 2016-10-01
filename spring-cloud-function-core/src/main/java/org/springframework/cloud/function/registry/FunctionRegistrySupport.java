/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.registry;

import java.util.function.Function;

import org.springframework.cloud.function.compiler.CompiledFunctionFactory;
import org.springframework.cloud.function.compiler.FunctionCompiler;
import org.springframework.cloud.function.compiler.FunctionFactory;
import org.springframework.cloud.function.compiler.java.SimpleClassLoader;
import org.springframework.util.Assert;

/**
 * @author Mark Fisher
 */
public abstract class FunctionRegistrySupport implements FunctionRegistry {

	private final FunctionCompiler compiler = new FunctionCompiler();

	private final SimpleClassLoader classLoader = new SimpleClassLoader(FunctionRegistrySupport.class.getClassLoader());

	@Override
	@SuppressWarnings("unchecked")
	public <T, R> Function<T, R> compose(String... functionNames) {
		@SuppressWarnings("rawtypes")
		Function function = this.lookup(functionNames[0]);
		for (int i = 1; i < functionNames.length; i++) {
			function = function.andThen(this.lookup(functionNames[i]));
		}
		return function;
	}

	protected <T, R> CompiledFunctionFactory<T, R> compile(String name, String code) {
		return this.compiler.compile(name, code);
	}

	@SuppressWarnings("unchecked")
	protected <T, R> Function<T, R> deserialize(String name, byte[] bytes) {
		Assert.hasLength(name, "name must not be empty");
		String firstLetter = name.substring(0, 1).toUpperCase();
		name = (name.length() > 1) ? firstLetter + name.substring(1) : firstLetter;
		String className = String.format("%s.%sFunctionFactory", FunctionCompiler.class.getPackage().getName(), name); 
		Class<?> factoryClass = this.classLoader.defineClass(className, bytes);
		try {
			return ((FunctionFactory<T, R>) factoryClass.newInstance()).getFunction();
		}
		catch (InstantiationException | IllegalAccessException e) {
			throw new IllegalArgumentException("failed to deserialize function", e);
		}
	}
}
