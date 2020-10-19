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
import reactor.core.publisher.Mono;

/**
 * Wrapper to mark function {@code Function<Flux<?>, Mono<?>>}.
 *
 * While it may look similar to {@link FluxedConsumer} the fundamental difference is that
 * this class represents a function that returns {@link Mono} of type {@code <O>}, while
 * {@link FluxedConsumer} is a consumer that has been decorated as
 * {@code Function<Flux<?>, Mono<Void>>}.
 *
 * @param <I> type of {@link Flux} input of the target function
 * @param <O> type of {@link Mono} output of the target function
 * @author Oleg Zhurakousky
 * @since 2.0
 *
 * @deprecated since 3.1 no longer used by the framework
 */
@Deprecated
public class FluxToMonoFunction<I, O>
		extends WrappedFunction<I, O, Flux<I>, Mono<O>, Function<Flux<I>, Mono<O>>> {

	public FluxToMonoFunction(Function<Flux<I>, Mono<O>> target) {
		super(target);
	}

	@Override
	public Mono<O> apply(Flux<I> input) {
		return this.getTarget().apply(input);
	}

}
