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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.cloud.function.compiler.CompiledFunctionFactory;
import org.springframework.cloud.function.compiler.FunctionCompiler;
import org.springframework.cloud.function.compiler.FunctionFactory;
import org.springframework.cloud.function.compiler.java.SimpleClassLoader;
import org.springframework.util.Assert;

/**
 * @author Mark Fisher
 */
public abstract class AbstractFunctionRegistry implements FunctionRegistry {

	private static final Map<String, FunctionFactory<?, ?>> FACTORY_CACHE = new HashMap<>();

	private final FunctionCompiler compiler = new FunctionCompiler();

	private final SimpleClassLoader classLoader = new SimpleClassLoader(AbstractFunctionRegistry.class.getClassLoader());

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

	@Override
	public final <T, R> Function<T, R> lookup(String name) {
		@SuppressWarnings("unchecked")
		FunctionFactory<T, R> factory = (FunctionFactory<T, R>) FACTORY_CACHE.get(name);
		if (factory != null) {
			return factory.getFunction();
		}
		return this.doLookup(name);
	}

	protected abstract <T, R> Function<T, R> doLookup(String name);

	protected <T, R> CompiledFunctionFactory<T, R> compile(String name, String code) {
		return this.compiler.compile(name, code);
	}

	@SuppressWarnings("unchecked")
	protected <T, R> Function<T, R> deserialize(final String name, byte[] bytes) {
		Assert.hasLength(name, "name must not be empty");
		String firstLetter = name.substring(0, 1).toUpperCase();
		String upperCasedName = (name.length() > 1) ? firstLetter + name.substring(1) : firstLetter;
		String className = String.format("%s.%sFunctionFactory", FunctionCompiler.class.getPackage().getName(), upperCasedName); 
		Class<?> factoryClass = this.classLoader.defineClass(className, bytes);
		try {
			FunctionFactory<T, R> factory = ((FunctionFactory<T, R>) factoryClass.newInstance());
			FACTORY_CACHE.put(name, factory);
			return factory.getFunction();
		}
		catch (InstantiationException | IllegalAccessException e) {
			throw new IllegalArgumentException("failed to deserialize function", e);
		}
	}
}
