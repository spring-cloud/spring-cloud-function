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

import java.util.function.Function;

import reactor.core.publisher.Flux;

/**
 * {@link Function} implementation that wraps a target Function so that the target's
 * simple input and output types will be wrapped as {@link Flux} instances.
 *
 * @author Mark Fisher
 *
 * @param <T> input type of target function
 * @param <R> output type of target function
 */
public class FluxFunction<T, R> implements Function<Flux<T>, Flux<R>>, FluxWrapper<Function<T, R>> {

	private final Function<T, R> function;

	public FluxFunction(Function<T, R> function) {
		this.function = function;
	}
	
	@Override
	public Function<T, R> getTarget() {
		return this.function;
	}

	@Override
	public Flux<R> apply(Flux<T> input) {
		return input.map(i -> this.function.apply(i));
	}
}
