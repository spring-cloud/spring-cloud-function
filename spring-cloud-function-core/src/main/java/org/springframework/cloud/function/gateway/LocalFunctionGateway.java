/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.gateway;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import org.springframework.cloud.function.invoker.FunctionInvokingRunnable;
import org.springframework.cloud.function.registry.FunctionRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.util.Assert;

/**
 * @author Mark Fisher
 */
public class LocalFunctionGateway implements FunctionGateway {

	private final FunctionRegistry registry;

	private final TaskScheduler scheduler;

	public LocalFunctionGateway(FunctionRegistry registry, TaskScheduler scheduler) {
		Assert.notNull(registry, "FunctionRegistry must not be null");
		Assert.notNull(scheduler, "TaskScheduler must not be null");
		this.registry = registry;
		this.scheduler = scheduler;
	}

	@Override
	public <T, R> R invoke(String functionName, T request) {
		Function<T, R> function = this.registry.lookup(functionName);
		return function.apply(request);
	}

	@Override
	public <T, R> void schedule(String functionName, Trigger trigger, Supplier<T> supplier, Consumer<R> consumer) {
		Function<T, R> function = this.registry.lookup(functionName);
		this.scheduler.schedule(new FunctionInvokingRunnable(supplier, function, consumer), trigger);
	}

	@Override
	public <T, R> void subscribe(Publisher<T> publisher, String functionName, final Consumer<R> consumer) {
		final Function<T, R> function = this.registry.lookup(functionName);
		publisher.subscribe(new Subscriber<T>() {

			@Override
			public void onComplete() {}

			@Override
			public void onError(Throwable error) {}

			@Override
			public void onNext(T next) {
				if (consumer != null) {
					consumer.accept(function.apply(next));
				}
				else {
					function.apply(next);
				}
			}

			@Override
			public void onSubscribe(Subscription subscription) {
				subscription.request(Long.MAX_VALUE);
			}
		});
	}
}
