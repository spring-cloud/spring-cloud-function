/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.integration.dsl;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.util.Assert;

/**
 * The helper class to lookup functions from the catalog in lazy manner and cache their
 * instances.
 *
 * @author Artem Bilan
 * @author Omer Celik
 * @since 4.0.3
 */
public class FunctionLookupHelper {

	private final FunctionCatalog functionCatalog;

	FunctionLookupHelper(FunctionCatalog functionCatalog) {
		this.functionCatalog = functionCatalog;
	}

	<P> Supplier<P> lookupSupplier(String functionDefinition) {
		Supplier<Supplier<P>> memoizedSupplier = lazyLookup(Supplier.class, functionDefinition);
		return () -> memoizedSupplier.get().get();
	}

	<P> Function<P, ?> lookupFunction(String functionDefinition) {
		Supplier<Function<P, ?>> memoizedFunction = lazyLookup(Function.class, functionDefinition);
		return (p) -> memoizedFunction.get().apply(p);
	}

	<P> Consumer<P> lookupConsumer(String consumerDefinition) {
		Supplier<Consumer<P>> memoizedConsumer = lazyLookup(Consumer.class, consumerDefinition);
		return (p) -> memoizedConsumer.get().accept(p);
	}

	private <T> Supplier<T> lazyLookup(Class<?> functionType, String functionDefinition) {
		return memoize(() -> requireNonNull(functionType, functionDefinition));
	}

	private <T> T requireNonNull(Class<?> functionType, String functionDefinition) {
		T function = this.functionCatalog.lookup(functionType, functionDefinition);
		Assert.notNull(function, () -> "No '" + functionDefinition + "' in the catalog");
		return function;
	}

	/**
	 * The delegate {@link Supplier#get()} is called exactly once and the result is
	 * cached.
	 * @param <T> Generic type of supplied value
	 * @param delegate The actual Supplier
	 * @return The memoized Supplier
	 */
	private static <T> Supplier<T> memoize(Supplier<? extends T> delegate) {
		AtomicReference<T> value = new AtomicReference<>();
		ReentrantLock lock = new ReentrantLock();
		return () -> {
			T val = value.get();
			if (val == null) {
				try {
					lock.lock();
					val = value.get();
					if (val == null) {
						val = delegate.get();
						value.set(val);
					}
				}
				finally {
					lock.unlock();
				}
			}
			return val;
		};
	}

}
