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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.cloud.function.compiler.CompiledFunctionFactory;
import org.springframework.cloud.function.compiler.ConsumerCompiler;
import org.springframework.cloud.function.compiler.ConsumerFactory;
import org.springframework.cloud.function.compiler.FunctionCompiler;
import org.springframework.cloud.function.compiler.FunctionFactory;
import org.springframework.cloud.function.compiler.SupplierCompiler;
import org.springframework.cloud.function.compiler.SupplierFactory;
import org.springframework.cloud.function.compiler.java.SimpleClassLoader;
import org.springframework.util.Assert;

/**
 * @author Mark Fisher
 */
public abstract class AbstractFunctionRegistry implements FunctionRegistry {

	private static final Map<String, ConsumerFactory<?>> CONSUMER_FACTORY_CACHE = new HashMap<>();

	private static final Map<String, FunctionFactory<?, ?>> FUNCTION_FACTORY_CACHE = new HashMap<>();

	private static final Map<String, SupplierFactory<?>> SUPPLIER_FACTORY_CACHE = new HashMap<>();

	private final ConsumerCompiler consumerCompiler = new ConsumerCompiler<>();

	private final FunctionCompiler functionCompiler = new FunctionCompiler<>();

	private final SupplierCompiler supplierCompiler = new SupplierCompiler<>();

	private final SimpleClassLoader classLoader = new SimpleClassLoader(AbstractFunctionRegistry.class.getClassLoader());

	@Override
	@SuppressWarnings("unchecked")
	public <T, R> Function<T, R> composeFunction(String... functionNames) {
		@SuppressWarnings("rawtypes")
		Function function = this.lookupFunction(functionNames[0]);
		for (int i = 1; i < functionNames.length; i++) {
			function = function.andThen(this.lookupFunction(functionNames[i]));
		}
		return function;
	}

	@Override
	public <T> Consumer<T> lookupConsumer(String name) {
		@SuppressWarnings("unchecked")
		ConsumerFactory<T> factory = (ConsumerFactory<T>) CONSUMER_FACTORY_CACHE.get(name);
		if (factory != null) {
			return factory.getResult();
		}
		return this.doLookupConsumer(name);
	}

	@Override
	public final <T, R> Function<T, R> lookupFunction(String name) {
		@SuppressWarnings("unchecked")
		FunctionFactory<T, R> factory = (FunctionFactory<T, R>) FUNCTION_FACTORY_CACHE.get(name);
		if (factory != null) {
			return factory.getResult();
		}
		return this.doLookupFunction(name);
	}

	@Override
	public <T> Supplier<T> lookupSupplier(String name) {
		@SuppressWarnings("unchecked")
		SupplierFactory<T> factory = (SupplierFactory<T>) SUPPLIER_FACTORY_CACHE.get(name);
		if (factory != null) {
			return factory.getResult();
		}
		return this.doLookupSupplier(name);
	}

	protected abstract <T> Consumer<T> doLookupConsumer(String name);

	protected abstract <T, R> Function<T, R> doLookupFunction(String name);

	protected abstract <T> Supplier<T> doLookupSupplier(String name);

	protected <T> CompiledFunctionFactory<Consumer<T>> compileConsumer(String name, String code) {
		return this.consumerCompiler.compile(name, code);
	}

	protected <T, R> CompiledFunctionFactory<Function<T, R>> compileFunction(String name, String code) {
		return this.functionCompiler.compile(name, code);
	}

	protected <T> CompiledFunctionFactory<Supplier<T>> compileSupplier(String name, String code) {
		return this.supplierCompiler.compile(name, code);
	}

	@SuppressWarnings("unchecked")
	protected <T> Consumer<T> deserializeConsumer(final String name, byte[] bytes) {
		String className = formatClassName(name, Consumer.class);
		Class<?> factoryClass = this.classLoader.defineClass(className, bytes);
		try {
			ConsumerFactory<T> factory = ((ConsumerFactory<T>) factoryClass.newInstance());
			CONSUMER_FACTORY_CACHE.put(name, factory);
			return factory.getResult();
		}
		catch (InstantiationException | IllegalAccessException e) {
			throw new IllegalArgumentException("failed to deserialize Consumer", e);
		}
	}

	@SuppressWarnings("unchecked")
	protected <T, R> Function<T, R> deserializeFunction(final String name, byte[] bytes) {
		String className = formatClassName(name, Function.class);
		Class<?> factoryClass = this.classLoader.defineClass(className, bytes);
		try {
			FunctionFactory<T, R> factory = ((FunctionFactory<T, R>) factoryClass.newInstance());
			FUNCTION_FACTORY_CACHE.put(name, factory);
			return factory.getResult();
		}
		catch (InstantiationException | IllegalAccessException e) {
			throw new IllegalArgumentException("failed to deserialize Function", e);
		}
	}

	@SuppressWarnings("unchecked")
	protected <T> Supplier<T> deserializeSupplier(final String name, byte[] bytes) {
		String className = formatClassName(name, Supplier.class);
		Class<?> factoryClass = this.classLoader.defineClass(className, bytes);
		try {
			SupplierFactory<T> factory = ((SupplierFactory<T>) factoryClass.newInstance());
			SUPPLIER_FACTORY_CACHE.put(name, factory);
			return factory.getResult();
		}
		catch (InstantiationException | IllegalAccessException e) {
			throw new IllegalArgumentException("failed to deserialize Supplier", e);
		}
	}

	private String formatClassName(String name, Class<?> type) {
		Assert.hasLength(name, "name must not be empty");
		String firstLetter = name.substring(0, 1).toUpperCase();
		String upperCasedName = (name.length() > 1) ? firstLetter + name.substring(1) : firstLetter;
		return String.format("%s.%s%sFactory", FunctionCompiler.class.getPackage().getName(), upperCasedName, type.getSimpleName()); 
	}
}
