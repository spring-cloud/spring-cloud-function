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

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 */
public class MessageSupplierTests {

	@Test
	public void plainSupplier() {
		MessageSupplier supplier = new MessageSupplier(input());
		StepVerifier.create(supplier.get()).assertNext(message -> {
			assertThat(message.getPayload()).isEqualTo("foo");
			assertThat(message.getHeaders()).isEmpty();
		});
	}

	@Test
	public void collectionSupplier() {
		MessageSupplier supplier = new MessageSupplier(inputs());
		StepVerifier.create(supplier.get()).assertNext(message -> {
			assertThat(message.getPayload()).isEqualTo("foo");
			assertThat(message.getHeaders()).isEmpty();
		}).assertNext(message -> {
			assertThat(message.getPayload()).isEqualTo("bar");
			assertThat(message.getHeaders()).isEmpty();
		});
	}

	@Test
	public void fluxSupplier() {
		MessageSupplier supplier = new MessageSupplier(flux());
		StepVerifier.create(supplier.get()).assertNext(message -> {
			assertThat(message.getPayload()).isEqualTo("foo");
			assertThat(message.getHeaders()).isEmpty();
		}).assertNext(message -> {
			assertThat(message.getPayload()).isEqualTo("bar");
			assertThat(message.getHeaders()).isEmpty();
		});
	}

	private Supplier<String> input() {
		return () -> "foo";
	}

	private Supplier<Collection<String>> inputs() {
		return () -> Arrays.asList("foo", "bar");
	}

	private Supplier<Flux<String>> flux() {
		return () -> Flux.just("foo", "bar");
	}

}
