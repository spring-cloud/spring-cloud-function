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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.springframework.cloud.function.core.FunctionCatalog;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Dave Syer
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 */
public class InMemoryFunctionCatalog implements FunctionCatalog {

	private final Map<String, Function<?, ?>> functions;

	private final Map<String, Consumer<?>> consumers;

	private final Map<String, Supplier<?>> suppliers;

	public InMemoryFunctionCatalog(Set<FunctionRegistration<?>> registrations) {
		Assert.notNull(registrations, "'registrations' must not be null");
		this.suppliers = new HashMap<>();
		this.functions = new HashMap<>();
		this.consumers = new HashMap<>();
		registrations.stream().forEach(reg -> reg.getNames().stream().forEach(name -> {
			if (reg.getTarget() instanceof Consumer){
				consumers.put(name, (Consumer<?>) reg.getTarget());
			}
			else if (reg.getTarget() instanceof Function){
				functions.put(name, (Function<?, ?>) reg.getTarget());
			}
			else if (reg.getTarget() instanceof Supplier){
				suppliers.put(name, (Supplier<?>) reg.getTarget());
			}
		}));
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Supplier<T> lookupSupplier(String name) {
		return (Supplier<T>) suppliers.get(name);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T, R> Function<T, R> lookupFunction(String name) {
		return (Function<T, R>) functions.get(name);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Consumer<T> lookupConsumer(String name) {
		return (Consumer<T>) consumers.get(name);
	}

	@Override
	public Set<String> getSupplierNames() {
		return suppliers.keySet();
	}

	@Override
	public Set<String> getFunctionNames() {
		return functions.keySet();
	}

	@Override
	public Set<String> getConsumerNames() {
		return consumers.keySet();
	}
}
