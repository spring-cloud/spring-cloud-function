/*
 * Copyright 2016-2017 the original author or authors.
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

package org.springframework.cloud.function.context;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.cloud.function.registry.FunctionCatalog;
import org.springframework.util.StringUtils;

/**
 * @author Dave Syer
 * @author Mark Fisher
 */
public class InMemoryFunctionCatalog implements FunctionCatalog {

	private final Map<String, Function<?, ?>> functions;

	private final Map<String, Consumer<?>> consumers;

	private final Map<String, Supplier<?>> suppliers;

	public InMemoryFunctionCatalog(Map<String, Supplier<?>> suppliers,
			Map<String, Function<?, ?>> functions, Map<String, Consumer<?>> consumers) {
		this.suppliers = suppliers;
		this.functions = functions;
		this.consumers = consumers;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Supplier<T> lookupSupplier(String name) {
		return (Supplier<T>) suppliers.get(name);
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public <T, R> Function<T, R> lookupFunction(String name) {
		if (name.indexOf(',') == -1) {
			return (Function<T, R>) functions.get(name);
		}
		String[] tokens = StringUtils.tokenizeToStringArray(name, ",");
		Function function = null;
		for (String token : tokens) {
			Function next = lookupFunction(token);
			function = (function == null) ? next : function.andThen(next);
		}
		return function;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Consumer<T> lookupConsumer(String name) {
		return (Consumer<T>) consumers.get(name);
	}
}
