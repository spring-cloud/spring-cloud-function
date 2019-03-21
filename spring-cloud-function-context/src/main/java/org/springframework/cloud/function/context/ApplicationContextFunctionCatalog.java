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
package org.springframework.cloud.function.context;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.cloud.function.registry.FunctionCatalog;

public class ApplicationContextFunctionCatalog implements FunctionCatalog {

	private final Map<String, Function<?, ?>> functions;
	private final Map<String, Consumer<?>> consumers;
	private final Map<String, Supplier<?>> suppliers;

	public ApplicationContextFunctionCatalog(Map<String, Function<?, ?>> functions,
			Map<String, Consumer<?>> consumers, Map<String, Supplier<?>> suppliers) {
		this.functions = functions;
		this.consumers = consumers;
		this.suppliers = suppliers;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> Consumer<T> lookupConsumer(String name) {
		return (Consumer<T>) consumers.get(name);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T, R> Function<T, R> lookupFunction(String name) {
		return (Function<T, R>) functions.get(name);
	}

	@Override
	public <T, R> Function<T, R> composeFunction(String... functionNames) {
		Function<T, R> function = this.lookupFunction(functionNames[0]);
		for (int i = 1; i < functionNames.length; i++) {
			function = function.andThen(this.lookupFunction(functionNames[i]));
		}
		return function;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> Supplier<T> lookupSupplier(String name) {
		return (Supplier<T>) suppliers.get(name);
	}

}
