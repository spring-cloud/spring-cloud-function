/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.cloud.function.context.catalog;

import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Test;
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.core.FluxFunction;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Oleg Zhurakousky
 *
 */
public class InMemoryFunctionCatalogTests {

	@Test
	public void testFunctionRegistration() {
		TestFunction function = new TestFunction();
		FunctionRegistration<TestFunction> registration = new FunctionRegistration<>(
				function, "foo").type(FunctionType.of(TestFunction.class));
		InMemoryFunctionCatalog catalog = new InMemoryFunctionCatalog();
		catalog.register(registration);
		FunctionRegistration<?> registration2 = catalog.getRegistration(function);
		assertThat(registration2).isSameAs(registration);
	}

	@Test
	public void testFunctionLookup() {
		TestFunction function = new TestFunction();
		FunctionRegistration<TestFunction> registration = new FunctionRegistration<>(
				function, "foo").type(FunctionType.of(TestFunction.class));
		InMemoryFunctionCatalog catalog = new InMemoryFunctionCatalog();
		catalog.register(registration);

		Object lookedUpFunction = catalog.lookup("hello");
		assertThat(lookedUpFunction).isNull();

		lookedUpFunction = catalog.lookup("foo");
		assertThat(lookedUpFunction).isNotNull();
		assertThat(catalog.lookupFunctionName(lookedUpFunction)).isEqualTo("foo");
		assertThat(catalog.getFunctionType("foo").getOutputType())
				.isEqualTo(String.class);
		assertThat(lookedUpFunction instanceof FluxFunction).isTrue();
	}

	@Test
	public void testFunctionComposition() {
		FunctionRegistration<UpperCase> upperCaseRegistration = new FunctionRegistration<>(
				new UpperCase(), "uppercase").type(FunctionType.of(UpperCase.class));
		FunctionRegistration<Reverse> reverseRegistration = new FunctionRegistration<>(
				new Reverse(), "reverse").type(FunctionType.of(Reverse.class));
		InMemoryFunctionCatalog catalog = new InMemoryFunctionCatalog();
		catalog.register(upperCaseRegistration);
		catalog.register(reverseRegistration);

		Function<Flux<String>, Flux<String>> lookedUpFunction = catalog
				.lookup("uppercase|reverse");
		assertThat(catalog.getFunctionType("uppercase|reverse").isMessage()).isFalse();

		assertThat(lookedUpFunction).isNotNull();
		assertThat(lookedUpFunction.apply(Flux.just("star")).blockFirst())
				.isEqualTo("RATS");
	}

	@Test
	public void testFunctionCompositionImplicit() {
		FunctionRegistration<Words> wordsRegistration = new FunctionRegistration<>(
				new Words(), "words").type(FunctionType.of(Words.class));
		FunctionRegistration<Reverse> reverseRegistration = new FunctionRegistration<>(
				new Reverse(), "reverse").type(FunctionType.of(Reverse.class));
		InMemoryFunctionCatalog catalog = new InMemoryFunctionCatalog();
		catalog.register(wordsRegistration);
		catalog.register(reverseRegistration);

		// There's only one function, we should be able to leave that blank
		Supplier<Flux<String>> lookedUpFunction = catalog.lookup("words|");
		assertThat(catalog.getFunctionType("words|").isMessage()).isFalse();

		assertThat(lookedUpFunction).isNotNull();
		assertThat(lookedUpFunction.get().blockFirst()).isEqualTo("olleh");
	}

	@Test
	public void testFunctionCompletelyImplicitComposition() {
		FunctionRegistration<Words> wordsRegistration = new FunctionRegistration<>(
				new Words(), "words").type(FunctionType.of(Words.class));
		FunctionRegistration<Reverse> reverseRegistration = new FunctionRegistration<>(
				new Reverse(), "reverse").type(FunctionType.of(Reverse.class));
		InMemoryFunctionCatalog catalog = new InMemoryFunctionCatalog();
		catalog.register(wordsRegistration);
		catalog.register(reverseRegistration);

		// There's only one function, we should be able to leave that blank
		Supplier<Flux<String>> lookedUpFunction = catalog.lookup("|");
		assertThat(catalog.getFunctionType("|").isMessage()).isFalse();

		assertThat(lookedUpFunction).isNotNull();
		assertThat(lookedUpFunction.get().blockFirst()).isEqualTo("olleh");
	}

	@Test
	public void testFunctionCompositionExplicit() {
		FunctionRegistration<Words> wordsRegistration = new FunctionRegistration<>(
				new Words(), "words").type(FunctionType.of(Words.class));
		FunctionRegistration<Reverse> reverseRegistration = new FunctionRegistration<>(
				new Reverse(), "reverse").type(FunctionType.of(Reverse.class));
		InMemoryFunctionCatalog catalog = new InMemoryFunctionCatalog();
		catalog.register(wordsRegistration);
		catalog.register(reverseRegistration);

		Supplier<Flux<String>> lookedUpFunction = catalog.lookup("words|reverse");
		assertThat(catalog.getFunctionType("words|reverse").isMessage()).isFalse();

		assertThat(lookedUpFunction).isNotNull();
		assertThat(lookedUpFunction.get().blockFirst()).isEqualTo("olleh");
	}

	@Test
	public void testFunctionCompositionWithMessages() {
		FunctionRegistration<UpperCaseMessage> upperCaseRegistration = new FunctionRegistration<>(
				new UpperCaseMessage(), "uppercase")
						.type(FunctionType.of(UpperCaseMessage.class));
		FunctionRegistration<ReverseMessage> reverseRegistration = new FunctionRegistration<>(
				new ReverseMessage(), "reverse")
						.type(FunctionType.of(ReverseMessage.class));
		InMemoryFunctionCatalog catalog = new InMemoryFunctionCatalog();
		catalog.register(upperCaseRegistration);
		catalog.register(reverseRegistration);

		Function<Flux<Message<String>>, Flux<Message<String>>> lookedUpFunction = catalog
				.lookup("uppercase|reverse");
		assertThat(catalog.getFunctionType("uppercase|reverse").isMessage()).isTrue();
		assertThat(catalog.lookupFunctionName(lookedUpFunction))
				.isEqualTo("uppercase|reverse");

		assertThat(lookedUpFunction).isNotNull();
		assertThat(lookedUpFunction
				.apply(Flux.just(MessageBuilder.withPayload("star").build())).blockFirst()
				.getPayload()).isEqualTo("RATS");
	}

	@Test
	public void testFunctionCompositionMixedMessages() {
		FunctionRegistration<UpperCaseMessage> upperCaseRegistration = new FunctionRegistration<>(
				new UpperCaseMessage(), "uppercase")
						.type(FunctionType.of(UpperCaseMessage.class));
		FunctionRegistration<Reverse> reverseRegistration = new FunctionRegistration<>(
				new Reverse(), "reverse").type(FunctionType.of(Reverse.class));
		InMemoryFunctionCatalog catalog = new InMemoryFunctionCatalog();
		catalog.register(upperCaseRegistration);
		catalog.register(reverseRegistration);

		Function<Flux<Message<String>>, Flux<Message<String>>> lookedUpFunction = catalog
				.lookup("uppercase|reverse");
		assertThat(catalog.getFunctionType("uppercase|reverse").isMessage()).isTrue();

		assertThat(lookedUpFunction).isNotNull();
		Message<String> message = lookedUpFunction.apply(Flux
				.just(MessageBuilder.withPayload("star").setHeader("foo", "bar").build()))
				.blockFirst();
		assertThat(message.getPayload()).isEqualTo("RATS");
		assertThat(message.getHeaders().get("foo")).isEqualTo("bar");
	}

	private static class Words implements Supplier<String> {

		@Override
		public String get() {
			return "hello";
		}

	}

	private static class UpperCase implements Function<String, String> {

		@Override
		public String apply(String t) {
			return t.toUpperCase();
		}

	}

	private static class UpperCaseMessage
			implements Function<Message<String>, Message<String>> {

		@Override
		public Message<String> apply(Message<String> t) {
			return MessageBuilder.withPayload(t.getPayload().toUpperCase())
					.copyHeaders(t.getHeaders()).build();
		}

	}

	private static class Reverse implements Function<String, String> {

		@Override
		public String apply(String t) {
			return new StringBuilder(t).reverse().toString();
		}

	}

	private static class ReverseMessage
			implements Function<Message<String>, Message<String>> {

		@Override
		public Message<String> apply(Message<String> t) {
			return MessageBuilder
					.withPayload(new StringBuilder(t.getPayload()).reverse().toString())
					.copyHeaders(t.getHeaders()).build();
		}

	}

	private static class TestFunction implements Function<Integer, String> {

		@Override
		public String apply(Integer t) {
			return "i=" + t;
		}

	}

}
