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

import org.junit.Test;
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.core.FluxFunction;

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
				function, "foo").type(FunctionType.of(TestFunction.class).getType());
		InMemoryFunctionCatalog catalog = new InMemoryFunctionCatalog();
		catalog.register(registration);
		FunctionRegistration<?> registration2 = catalog.getRegistration(function);
		assertThat(registration2).isSameAs(registration);
	}

	@Test
	public void testFunctionLookup() {
		TestFunction function = new TestFunction();
		FunctionRegistration<TestFunction> registration = new FunctionRegistration<>(
				function, "foo").type(FunctionType.of(TestFunction.class).getType());
		InMemoryFunctionCatalog catalog = new InMemoryFunctionCatalog();
		catalog.register(registration);

		Object lookedUpFunction = catalog.lookup("hello");
		assertThat(lookedUpFunction).isNull();

		lookedUpFunction = catalog.lookup("foo");
		assertThat(lookedUpFunction).isNotNull();
		assertThat(lookedUpFunction instanceof FluxFunction).isTrue();
	}

	@Test
	public void testFunctionComposition() {
		FunctionRegistration<UpperCase> upperCaseRegistration = new FunctionRegistration<>(
				new UpperCase(), "uppercase").type(FunctionType.of(UpperCase.class).getType());
		FunctionRegistration<Reverse> reverseRegistration = new FunctionRegistration<>(
				new Reverse(), "reverse").type(FunctionType.of(Reverse.class).getType());
		InMemoryFunctionCatalog catalog = new InMemoryFunctionCatalog();
		catalog.register(upperCaseRegistration);
		catalog.register(reverseRegistration);

		Function<Flux<String>, Flux<String>> lookedUpFunction = catalog.lookup("uppercase|reverse");

		assertThat(lookedUpFunction).isNotNull();
		assertThat(lookedUpFunction.apply(Flux.just("star")).blockFirst()).isEqualTo("RATS");
	}

	private static class UpperCase implements Function<String, String> {

		@Override
		public String apply(String t) {
			return t.toUpperCase();
		}

	}

	private static class Reverse implements Function<String, String> {

		@Override
		public String apply(String t) {
			return new StringBuilder(t).reverse().toString();
		}

	}

	private static class TestFunction implements Function<Integer, String> {

		@Override
		public String apply(Integer t) {
			return "i=" + t;
		}

	}

}
