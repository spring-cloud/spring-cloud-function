/*
 * Copyright 2016-2017 the original author or authors.
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
package org.springframework.cloud.function.context;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Test;

import org.springframework.core.ResolvableType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
public class FunctionTypeTests {

	@Test
	public void plainFunction() {
		FunctionType function = new FunctionType(IntegerToString.class);
		assertThat(function.getInputType()).isEqualTo(Integer.class);
		assertThat(function.getOutputType()).isEqualTo(String.class);
		assertThat(function.getInputWrapper()).isEqualTo(Integer.class);
		assertThat(function.getOutputWrapper()).isEqualTo(String.class);
		assertThat(function.isMessage()).isEqualTo(false);
	}

	@Test
	public void supplierOfRegistration() {
		FunctionType function = new FunctionType(SupplierOfRegistrationOfIntegerToString.class);
		assertThat(function.getInputType()).isEqualTo(Integer.class);
		assertThat(function.getOutputType()).isEqualTo(String.class);
		assertThat(function.getInputWrapper()).isEqualTo(Integer.class);
		assertThat(function.getOutputWrapper()).isEqualTo(String.class);
		assertThat(function.isMessage()).isEqualTo(false);
	}

	@Test
	public void supplier() {
		FunctionType function = new FunctionType(SupplierOfIntegerToString.class);
		assertThat(function.getInputType()).isEqualTo(Integer.class);
		assertThat(function.getOutputType()).isEqualTo(String.class);
		assertThat(function.getInputWrapper()).isEqualTo(Integer.class);
		assertThat(function.getOutputWrapper()).isEqualTo(String.class);
		assertThat(function.isMessage()).isEqualTo(false);
	}

	@Test
	public void genericFunction() {
		FunctionType function = new FunctionType(StringToMap.class);
		assertThat(function.getInputType()).isEqualTo(String.class);
		assertThat(function.getOutputType()).isEqualTo(Map.class);
		assertThat(function.getInputWrapper()).isEqualTo(String.class);
		assertThat(function.getOutputWrapper()).isEqualTo(Map.class);
		assertThat(function.isMessage()).isEqualTo(false);
	}

	@Test
	public void pojoFunction() {
		FunctionType function = new FunctionType(FooToFoo.class);
		assertThat(function.getInputType()).isEqualTo(Foo.class);
		assertThat(function.getOutputType()).isEqualTo(Bar.class);
		assertThat(function.getInputWrapper()).isEqualTo(Foo.class);
		assertThat(function.getOutputWrapper()).isEqualTo(Bar.class);
		assertThat(function.isMessage()).isEqualTo(false);
	}

	@Test
	public void fluxFunction() {
		FunctionType function = new FunctionType(FluxToFlux.class);
		assertThat(function.getInputType()).isEqualTo(Foo.class);
		assertThat(function.getOutputType()).isEqualTo(Bar.class);
		assertThat(function.getInputWrapper()).isEqualTo(Flux.class);
		assertThat(function.getOutputWrapper()).isEqualTo(Flux.class);
		assertThat(function.isMessage()).isEqualTo(false);
	}

	@Test
	public void fluxMessageFunction() {
		FunctionType function = new FunctionType(FluxMessageToFluxMessage.class);
		assertThat(function.getInputType()).isEqualTo(Foo.class);
		assertThat(function.getOutputType()).isEqualTo(Bar.class);
		assertThat(function.getInputWrapper()).isEqualTo(Flux.class);
		assertThat(function.getOutputWrapper()).isEqualTo(Flux.class);
		assertThat(function.isMessage()).isEqualTo(true);
	}

	@Test
	public void plainFunctionFromType() {
		Type type = ResolvableType
				.forClassWithGenerics(Function.class, Integer.class, String.class)
				.getType();
		FunctionType function = new FunctionType(type);
		assertThat(function.getInputType()).isEqualTo(Integer.class);
		assertThat(function.getOutputType()).isEqualTo(String.class);
		assertThat(function.getInputWrapper()).isEqualTo(Integer.class);
		assertThat(function.getOutputWrapper()).isEqualTo(String.class);
		assertThat(function.isMessage()).isEqualTo(false);
	}

	@Test
	public void pojoConsumerFromType() {
		Type type = ResolvableType.forClassWithGenerics(Consumer.class, Foo.class)
				.getType();
		FunctionType function = new FunctionType(type);
		assertThat(function.getInputType()).isEqualTo(Foo.class);
		assertThat(function.getOutputType()).isEqualTo(Void.class);
		assertThat(function.getInputWrapper()).isEqualTo(Foo.class);
		assertThat(function.getOutputWrapper()).isEqualTo(Void.class);
		assertThat(function.isMessage()).isEqualTo(false);
	}

	@Test
	public void pojoSupplierFromType() {
		Type type = ResolvableType.forClassWithGenerics(Supplier.class, Foo.class)
				.getType();
		FunctionType function = new FunctionType(type);
		assertThat(function.getInputType()).isEqualTo(Void.class);
		assertThat(function.getOutputType()).isEqualTo(Foo.class);
		assertThat(function.getInputWrapper()).isEqualTo(Void.class);
		assertThat(function.getOutputWrapper()).isEqualTo(Foo.class);
		assertThat(function.isMessage()).isEqualTo(false);
	}

	@Test
	public void pojoSupplierFrom() {
		FunctionType function = new FunctionType(Supplier.class).to(Foo.class);
		assertThat(function.getInputType()).isEqualTo(Void.class);
		assertThat(function.getOutputType()).isEqualTo(Foo.class);
		assertThat(function.getInputWrapper()).isEqualTo(Void.class);
		assertThat(function.getOutputWrapper()).isEqualTo(Foo.class);
		assertThat(function.isMessage()).isEqualTo(false);
	}

	@Test
	public void pojoSupplier() {
		FunctionType function = FunctionType.supplier(Foo.class);
		assertThat(function.getInputType()).isEqualTo(Void.class);
		assertThat(function.getOutputType()).isEqualTo(Foo.class);
		assertThat(function.getInputWrapper()).isEqualTo(Void.class);
		assertThat(function.getOutputWrapper()).isEqualTo(Foo.class);
		assertThat(function.isMessage()).isEqualTo(false);
	}

	@Test
	public void pojoConsumer() {
		FunctionType function = FunctionType.consumer(Foo.class);
		assertThat(function.getOutputType()).isEqualTo(Void.class);
		assertThat(function.getInputType()).isEqualTo(Foo.class);
		assertThat(function.getOutputWrapper()).isEqualTo(Void.class);
		assertThat(function.getInputWrapper()).isEqualTo(Foo.class);
		assertThat(function.isMessage()).isEqualTo(false);
	}

	@Test
	public void plainFunctionFromApi() {
		FunctionType function = FunctionType.from(Integer.class).to(String.class);
		assertThat(function.getInputType()).isEqualTo(Integer.class);
		assertThat(function.getOutputType()).isEqualTo(String.class);
		assertThat(function.getInputWrapper()).isEqualTo(Integer.class);
		assertThat(function.getOutputWrapper()).isEqualTo(String.class);
		assertThat(function.isMessage()).isEqualTo(false);
	}

	@Test
	public void fluxMessageFunctionFromType() {
		Type type = ResolvableType
				.forClassWithGenerics(Function.class,
						ResolvableType.forClassWithGenerics(
								Flux.class,
								ResolvableType.forClassWithGenerics(Message.class,
										Foo.class)),
						ResolvableType.forClassWithGenerics(Flux.class, ResolvableType
								.forClassWithGenerics(Message.class, Bar.class)))
				.getType();
		FunctionType function = new FunctionType(type);
		assertThat(function.getInputType()).isEqualTo(Foo.class);
		assertThat(function.getOutputType()).isEqualTo(Bar.class);
		assertThat(function.getInputWrapper()).isEqualTo(Flux.class);
		assertThat(function.getOutputWrapper()).isEqualTo(Flux.class);
		assertThat(function.isMessage()).isEqualTo(true);
	}

	@Test
	public void fluxMessageFunctionFromApi() {
		FunctionType function = FunctionType.from(Foo.class).to(Bar.class).message()
				.wrap(Flux.class);
		assertThat(function.getInputType()).isEqualTo(Foo.class);
		assertThat(function.getOutputType()).isEqualTo(Bar.class);
		assertThat(function.getInputWrapper()).isEqualTo(Flux.class);
		assertThat(function.getOutputWrapper()).isEqualTo(Flux.class);
		assertThat(function.isMessage()).isEqualTo(true);
	}

	@Test
	public void compose() {
		FunctionType input = FunctionType.from(Foo.class).to(Bar.class).wrap(Flux.class);
		FunctionType output = FunctionType.from(Bar.class).to(String.class);
		FunctionType function = FunctionType.compose(input, output);
		assertThat(function.getInputType()).isEqualTo(Foo.class);
		assertThat(function.getOutputType()).isEqualTo(String.class);
		assertThat(function.getInputWrapper()).isEqualTo(Flux.class);
		assertThat(function.getOutputWrapper()).isEqualTo(Flux.class);
		assertThat(function.isMessage()).isEqualTo(false);
	}

	@Test
	public void idempotentMessage() {
		FunctionType function = FunctionType.from(Foo.class).to(Bar.class).message()
				.wrap(Flux.class);
		assertThat(function).isSameAs(function.message());
	}

	@Test
	public void idempotentWrapper() {
		FunctionType function = FunctionType.from(Foo.class).to(Bar.class).message()
				.wrap(Flux.class);
		assertThat(function).isSameAs(function.wrap(Flux.class));
	}

	@Test
	public void nonWrapper() {
		FunctionType function = FunctionType.from(Foo.class).to(Bar.class);
		assertThat(function).isSameAs(function.wrap(Object.class));
	}

	private static class SupplierOfRegistrationOfIntegerToString implements Supplier<FunctionRegistration<Function<Integer, String>>> {
		@Override
		public FunctionRegistration<Function<Integer, String>> get() {
			return new FunctionRegistration<Function<Integer,String>>(new IntegerToString(), "ints");
		}
	}
	
	private static class SupplierOfIntegerToString implements Supplier<Function<Integer, String>> {
		@Override
		public Function<Integer, String> get() {
			return new IntegerToString();
		}
	}
	
	private static class IntegerToString implements Function<Integer, String> {
		@Override
		public String apply(Integer t) {
			return "" + t;
		}
	}

	private static class StringToMap implements Function<String, Map<String, Integer>> {
		@Override
		public Map<String, Integer> apply(String t) {
			return Collections.emptyMap();
		}
	}

	private static class FooToFoo implements Function<Foo, Bar> {
		@Override
		public Bar apply(Foo t) {
			return new Bar();
		}
	}

	private static class FluxToFlux implements Function<Flux<Foo>, Flux<Bar>> {
		@Override
		public Flux<Bar> apply(Flux<Foo> t) {
			return t.map(f -> new Bar());
		}
	}

	private static class FluxMessageToFluxMessage
			implements Function<Flux<Message<Foo>>, Flux<Message<Bar>>> {
		@Override
		public Flux<Message<Bar>> apply(Flux<Message<Foo>> t) {
			return t.map(f -> MessageBuilder.withPayload(new Bar())
					.copyHeadersIfAbsent(f.getHeaders()).build());
		}
	}

	private static class Foo {
	}

	private static class Bar {
	}
}
