/*
 * Copyright 2012-2019 the original author or authors.
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

import java.util.function.Function;

import reactor.core.publisher.Flux;

/**
 * {@link Function} implementation that wraps a target Function so that the target's
 * simple input and output types will be wrapped as {@link Flux} instances.
 *
 * @param <I> input type of target function
 * @param <O> output type of target function
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 *
 * @deprecated since 3.1 no longer used by the framework
 */
@Deprecated
public class FluxFunction<I, O>
		extends WrappedFunction<I, O, Flux<I>, Flux<O>, Function<I, O>> {

	public FluxFunction(Function<I, O> target) {
		super(target);
	}

	@Override
	public Flux<O> apply(Flux<I> input) {
		return input.map(value -> this.getTarget().apply(value));
	}

}
