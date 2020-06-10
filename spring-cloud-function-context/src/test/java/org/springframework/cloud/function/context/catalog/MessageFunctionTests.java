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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.cloud.function.core.FluxConsumer;
import org.springframework.cloud.function.core.FluxFunction;
import org.springframework.cloud.function.core.FluxToMonoFunction;
import org.springframework.cloud.function.core.MonoToFluxFunction;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 */
public class MessageFunctionTests {

	private List<String> items = new ArrayList<>();

	@Test
	public void plainFunction() {
		MessageFunction function = new MessageFunction(uppercase());
		Publisher<Message<?>> result = function.apply(Flux
				.just(MessageBuilder.withPayload("foo").setHeader("foo", "bar").build()));
		StepVerifier.create(result).assertNext(message -> {
			assertThat(message.getPayload()).isEqualTo("FOO");
			assertThat(message.getHeaders()).containsEntry("foo", "bar");
		});
	}

	@Test
	public void fluxFunction() {
		MessageFunction function = new MessageFunction(new FluxFunction<>(uppercase()));
		Publisher<Message<?>> result = function.apply(Flux
				.just(MessageBuilder.withPayload("foo").setHeader("foo", "bar").build()));
		StepVerifier.create(result).assertNext(message -> {
			assertThat(message.getPayload()).isEqualTo("FOO");
			assertThat(message.getHeaders()).containsEntry("foo", "bar");
		});
	}

	@Test
	public void fluxToMonoFunction() {
		MessageFunction function = new MessageFunction(
				new FluxToMonoFunction<String, String>(
						flux -> flux.next().map(uppercase())));
		Publisher<Message<?>> result = function.apply(Flux
				.just(MessageBuilder.withPayload("foo").setHeader("foo", "bar").build()));
		StepVerifier.create(result).assertNext(message -> {
			assertThat(message.getPayload()).isEqualTo("FOO");
			assertThat(message.getHeaders()).containsEntry("foo", "bar");
		});
	}

	@Test
	public void monoToFunction() {
		MessageFunction function = new MessageFunction(
				new MonoToFluxFunction<String, String>(
						mono -> Flux.from(mono.map(uppercase()))));
		Publisher<Message<?>> result = function.apply(Flux
				.just(MessageBuilder.withPayload("foo").setHeader("foo", "bar").build()));
		StepVerifier.create(result).assertNext(message -> {
			assertThat(message.getPayload()).isEqualTo("FOO");
			assertThat(message.getHeaders()).containsEntry("foo", "bar");
		});
	}

	@Test
	public void fluxConsumer() {
		MessageFunction function = new MessageFunction(new FluxConsumer<>(stash()));
		Publisher<Message<?>> result = function.apply(Flux
				.just(MessageBuilder.withPayload("foo").setHeader("foo", "bar").build()));
		StepVerifier.create(result).assertNext(message -> {
			assertThat(message.getPayload()).isEqualTo(null);
			assertThat(message.getHeaders()).containsEntry("foo", "bar");
			assertThat(this.items).hasSize(1);
		});
	}

	private Consumer<String> stash() {
		return value -> this.items.add(value);
	}

	private Function<String, String> uppercase() {
		return value -> value.toUpperCase();
	}

}
