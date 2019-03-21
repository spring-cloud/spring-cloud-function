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

package org.springframework.cloud.function.context.catalog;

import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.core.FluxSupplier;
import org.springframework.cloud.function.core.MonoSupplier;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * @author Dave Syer
 */
public class MessageSupplier implements Supplier<Publisher<Message<?>>> {

	private Supplier<?> delegate;

	public MessageSupplier(Supplier<?> delegate) {
		this.delegate = delegate;
	}

	@Override
	public Publisher<Message<?>> get() {
		if (this.delegate instanceof FluxSupplier) {
			return ((Flux<?>) this.delegate.get())
					.map(value -> MessageBuilder.withPayload(value).build());
		}
		if (this.delegate instanceof MonoSupplier) {
			return ((Mono<?>) this.delegate.get())
					.map(value -> MessageBuilder.withPayload(value).build());
		}
		Object product = this.delegate.get();
		if (product instanceof Publisher) {
			return Flux.from((Publisher<?>) product)
					.map(value -> MessageBuilder.withPayload(value).build());
		}
		if (product instanceof Iterable) {
			return Flux.fromIterable((Iterable<?>) product)
					.map(value -> MessageBuilder.withPayload(value).build());
		}
		return Mono.just(MessageBuilder.withPayload(product).build());
	}

}
