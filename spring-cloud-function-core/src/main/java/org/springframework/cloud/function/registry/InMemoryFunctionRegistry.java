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

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Mark Fisher
 */
public class InMemoryFunctionRegistry extends AbstractFunctionRegistry {

	private final ConcurrentHashMap<String, Consumer<?>> consumerMap = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, Function<?, ?>> functionMap = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, Supplier<?>> supplierMap = new ConcurrentHashMap<>();

	@Override
	@SuppressWarnings("unchecked")
	protected Consumer<?> doLookupConsumer(String name) {
		return this.consumerMap.get(name);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Function<?, ?> doLookupFunction(String name) {
		return this.functionMap.get(name);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected Supplier<?> doLookupSupplier(String name) {
		return this.supplierMap.get(name);
	}

	@Override
	public void registerConsumer(String name, String consumer) {
		this.consumerMap.put(name, this.compileConsumer(name, consumer).getResult());
	}

	@Override
	public void registerFunction(String name, String function) {
		this.functionMap.put(name, this.compileFunction(name, function).getResult());
	}

	@Override
	public void registerSupplier(String name, String supplier) {
		this.supplierMap.put(name, this.compileSupplier(name, supplier).getResult());
	}
}
