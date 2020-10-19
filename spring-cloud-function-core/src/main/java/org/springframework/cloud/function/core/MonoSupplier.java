/*
 * Copyright 2019-2019 the original author or authors.
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

package org.springframework.cloud.function.core;

import java.util.function.Supplier;

import reactor.core.publisher.Mono;

/**
 * {@link Supplier} implementation that wraps a target Supplier so that the target's
 * simple output type will be wrapped in a {@link Mono} instance.
 *
 * @param <T> output type of target supplier
 * @author Mark Fisher
 * @since 2.1
 *
 * @deprecated since 3.1 no longer used by the framework
 */
@Deprecated
public class MonoSupplier<T> implements Supplier<Mono<T>>, FluxWrapper<Supplier<T>> {

	private final Supplier<T> supplier;

	public MonoSupplier(Supplier<T> supplier) {
		this.supplier = supplier;
	}

	@Override
	public Supplier<T> getTarget() {
		return this.supplier;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Mono<T> get() {
		Object result = this.supplier.get();
		return Mono.just((T) result);
	}

}
