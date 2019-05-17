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

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.core.FluxConsumer;
import org.springframework.cloud.function.core.FluxFunction;
import org.springframework.cloud.function.core.FluxToMonoFunction;
import org.springframework.cloud.function.core.FluxedFunction;
import org.springframework.cloud.function.core.MonoToFluxFunction;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

/**
 * @author Dave Syer
 * @since 2.1
 */
public class MessageFunction
		implements Function<Publisher<?>, Publisher<Message<?>>> {

	private final Function<?, ?> delegate;

	public MessageFunction(Function<?, ?> delegate) {
		this.delegate = delegate;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Publisher<Message<?>> apply(Publisher<?> input) {
		Flux<Object> incomingFlux = Flux.from(input);
		Flux<Message<?>> flux = incomingFlux.map(value -> {
			if (!(value instanceof Message)) {
				return MessageBuilder.withPayload(value).build();
			}
			return (Message<?>) value;
		});

		if (this.delegate instanceof FluxFunction) {
			Function<Object, Object> target = (Function<Object, Object>) ((FluxFunction<?, ?>) this.delegate)
					.getTarget();
			return flux.map(
					value -> MessageBuilder.withPayload(target.apply(value.getPayload()))
							.copyHeaders(value.getHeaders()).build());
		}
		if (this.delegate instanceof MonoToFluxFunction) {
			Function<Mono<Object>, Flux<Object>> target = ((MonoToFluxFunction<Object, Object>) this.delegate)
					.getTarget();
			return flux.next()
					.flatMapMany(value -> target.apply(Mono.just(value.getPayload()))
							.map(object -> MessageBuilder.withPayload(object)
									.copyHeaders(value.getHeaders()).build()));
		}
		if (this.delegate instanceof FluxToMonoFunction) {
			Function<Flux<Object>, Mono<Object>> target = ((FluxToMonoFunction<Object, Object>) this.delegate)
					.getTarget();
			AtomicReference<MessageHeaders> headers = new AtomicReference<>();
			return target.apply(flux.map(messsage -> {
				headers.set(messsage.getHeaders());
				return messsage.getPayload();
			})).map(payload -> MessageBuilder.withPayload(payload)
					.copyHeaders(headers.get()).build());
		}
		if (this.delegate instanceof FluxConsumer) {
			FluxConsumer<Object> target = ((FluxConsumer<Object>) this.delegate);
			AtomicReference<MessageHeaders> headers = new AtomicReference<>();
			Mono<Void> mapped = target.apply(flux.map(messsage -> {
				headers.set(messsage.getHeaders());
				return messsage.getPayload();
			}));
			return mapped.map(value -> MessageBuilder.createMessage(null, headers.get()));
		}

		// TODO: cover the case that delegate is actually Function<Flux,Flux>
		if (this.delegate instanceof FluxedFunction) {
			Function<Flux<Object>, Flux<Object>> target = ((FluxedFunction) this.delegate);
			return (Flux) flux.map(value -> ((Message) value).getPayload()).transform(target);
		}
		Function function = this.delegate;
		return flux.map(
				value -> {
					return MessageBuilder.withPayload(function.apply(value.getPayload()))
						.copyHeaders(value.getHeaders()).build();
						});
	}
}
