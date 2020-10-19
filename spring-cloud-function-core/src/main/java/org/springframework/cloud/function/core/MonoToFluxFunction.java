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
 * Marker wrapper for target Function&lt;Mono, Flux&gt;.
 *
 * @param <I> type of {@link Mono} input of the target function
 * @param <O> type of {@link Flux} output of the target function
 * @author Oleg Zhurakousky
 * @since 2.0
 *
 * @deprecated since 3.1 no longer used by the framework
 */
@Deprecated
public class MonoToFluxFunction<I, O>
		extends WrappedFunction<I, O, Mono<I>, Flux<O>, Function<Mono<I>, Flux<O>>> {

	public MonoToFluxFunction(Function<Mono<I>, Flux<O>> target) {
		super(target);
	}

	@Override
	public Flux<O> apply(Mono<I> input) {
		return this.getTarget().apply(input);
	}

}
