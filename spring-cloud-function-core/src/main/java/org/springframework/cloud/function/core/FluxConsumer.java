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

import java.util.function.Consumer;
import java.util.function.Function;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Wrapper for a {@link Consumer} implementation that converts a non-reactive consumer
 * into a reactive function.
 *
 * @author Dave Syer
 *
 * @param <T> input type of target consumer
 */
public class FluxConsumer<T>
		implements Function<Flux<T>, Mono<Void>>, FluxWrapper<Consumer<T>> {

	private final Consumer<T> consumer;

	public FluxConsumer(Consumer<T> consumer) {
		this.consumer = consumer;
	}

	@Override
	public Consumer<T> getTarget() {
		return this.consumer;
	}

	@Override
	public Mono<Void> apply(Flux<T> input) {
		return input.doOnNext(consumer).then();
	}
}
