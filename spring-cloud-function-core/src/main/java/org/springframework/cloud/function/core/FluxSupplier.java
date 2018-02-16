/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.function.core;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.stream.Stream;

import reactor.core.publisher.Flux;

/**
 * {@link Supplier} implementation that wraps a target Supplier so that the
 * target's simple output type will be wrapped in a {@link Flux} instance.
 * If a {@link Duration} is provided, the Flux will produce output
 * periodically, invoking the target Supplier's {@code get} method at each
 * interval. If no Duration is provided, the target will be invoked only once.
 *
 * @author Mark Fisher
 *
 * @param <T> output type of target supplier
 */
public class FluxSupplier<T> implements Supplier<Flux<T>>, FluxWrapper<Supplier<T>>  {

	private final Supplier<T> supplier;

	private final Duration period;

	public FluxSupplier(Supplier<T> supplier) {
		this(supplier, null);
	}
	
	public FluxSupplier(Supplier<T> supplier, Duration period) {
		this.supplier = supplier;
		this.period = period;
	}

	@Override
	public Supplier<T> getTarget() {
		return this.supplier;
	}
	
	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Flux<T> get() {
		if (this.period != null) {
			return Flux.interval(this.period).map(i->this.supplier.get());
		}
		Object result = this.supplier.get();
		if (result instanceof Stream) {
			return Flux.fromStream((Stream) result);
		}
		return Flux.just((T) result);
	}
}
