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

package org.springframework.cloud.function.context.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration.BeanFactoryFunctionCatalog;

import static org.assertj.core.api.Assertions.assertThat;
/**
 * @author Dave Syer
 *
 */
public class BeanFactoryFunctionCatalogTests {

	private BeanFactoryFunctionCatalog processor = new BeanFactoryFunctionCatalog();

	@Test
	public void basicRegistrationFeatures() {
		this.processor.register(new FunctionRegistration<>(new Foos(), "foos"));
		Function<Flux<Integer>, Flux<String>> foos = this.processor.lookup(Function.class,
				"foos");
		assertThat(foos.apply(Flux.just(2)).blockFirst()).isEqualTo("4");
	}

	@Test
	public void lookupFunctionWithEmptyName() {
		this.processor.register(new FunctionRegistration<>(new Foos(), "foos"));
		Function<Flux<Integer>, Flux<String>> foos = this.processor.lookup(Function.class,
				"");
		assertThat(foos.apply(Flux.just(2)).blockFirst()).isEqualTo("4");
	}

	@Test
	public void lookupFunctionWithNoType() {
		this.processor.register(new FunctionRegistration<>(new Foos(), "foos"));
		Function<Flux<Integer>, Flux<String>> foos = this.processor.lookup("foos");
		assertThat(foos.apply(Flux.just(2)).blockFirst()).isEqualTo("4");
	}

	@Test
	public void registerFunctionWithType() {
		this.processor.register(new FunctionRegistration<Function<Integer, String>>(
				(Integer i) -> "i=" + i, "foos").type(
						FunctionType.from(Integer.class).to(String.class).getType()));
		Function<Flux<Integer>, Flux<String>> foos = this.processor.lookup(Function.class,
				"");
		assertThat(foos.apply(Flux.just(2)).blockFirst()).isEqualTo("i=2");
	}

	@Test
	public void registerFunctionWithFluxType() {
		this.processor
				.register(new FunctionRegistration<Function<Flux<Integer>, Flux<String>>>(
						ints -> ints.map(i -> "i=" + i), "foos")
								.type(FunctionType.from(Integer.class).to(String.class)
										.wrap(Flux.class).getType()));
		Function<Flux<Integer>, Flux<String>> foos = this.processor.lookup(Function.class,
				"");
		assertThat(foos.apply(Flux.just(2)).blockFirst()).isEqualTo("i=2");
	}

	@Test
	public void registerFunctionWithMonoType() {
		this.processor.register(
				new FunctionRegistration<Function<Flux<String>, Mono<Map<String, Integer>>>>(
						flux -> flux.collect(HashMap::new,
								(map, word) -> map.merge(word, 1, Integer::sum)),
						"foos").type(
								FunctionType.from(String.class).to(Map.class)
										.wrap(Flux.class, Mono.class).getType()));
		Function<Flux<String>, Mono<Map<String, Integer>>> foos = this.processor
				.lookup(Function.class, "");
		assertThat(foos.apply(Flux.just("one", "one", "two")).block())
				.containsEntry("one", 2);
	}

	@Test
	public void lookupNonExistentConsumerWithEmptyName() {
		this.processor.register(new FunctionRegistration<>(new Foos(), "foos"));
		Consumer<Flux<String>> foos = this.processor.lookup(Consumer.class, "");
		assertThat(foos).isNull();
	}

	@Test
	public void composeFunction() {
		this.processor.register(new FunctionRegistration<>(new Foos(), "foos"));
		this.processor.register(new FunctionRegistration<>(new Bars(), "bars"));
		Function<Flux<Integer>, Flux<String>> foos = this.processor.lookup(Function.class,
				"foos,bars");
		assertThat(foos.apply(Flux.just(2)).blockFirst()).isEqualTo("Hello 4");
	}

	@Test
	public void composeWithFiniteFunction() {
		Function<String, String> func1 = x -> x.toUpperCase();
		this.processor.register(new FunctionRegistration<>(func1, "func1"));
		this.processor.register(
				new FunctionRegistration<>(new FluxThenMonoFunction(), "func2"));
		Function<Flux<String>, Mono<Long>> foos = this.processor.lookup(Function.class,
				"func1,func2");
		assertThat(foos.apply(Flux.fromArray(new String[] { "a", "b", "c" })).block())
				.isEqualTo(3);
	}

	@Test
	public void composeWithFiniteFunctionAndContinueWithCompatible() {
		Function<String, String> func1 = x -> x.toUpperCase();
		this.processor.register(new FunctionRegistration<>(func1, "func1"));
		this.processor.register(
				new FunctionRegistration<>(new FluxThenMonoFunction(), "func2"));
		this.processor.register(
				new FunctionRegistration<>(new MonoThenFluxFunction(), "func3"));
		Function<Flux<String>, Flux<Integer>> foos = this.processor.lookup(Function.class,
				"func1,func2,func3");
		assertThat(foos.apply(Flux.fromArray(new String[] { "a", "b", "c" }))
				.collectList().block().size()).isEqualTo(3);
	}

	@Test(expected = IllegalStateException.class)
	public void composeIncompatibleFunctions() {
		Function<String, String> func1 = x -> x.toUpperCase();
		this.processor.register(new FunctionRegistration<>(func1, "func1"));
		this.processor.register(
				new FunctionRegistration<>(new FluxThenMonoFunction(), "func2"));
		this.processor.lookup(Function.class, "func2,func1");
	}

	@Test
	public void composeSupplier() {
		this.processor.register(new FunctionRegistration<>(new Source(), "numbers"));
		this.processor.register(new FunctionRegistration<>(new Foos(), "foos"));
		Supplier<Flux<String>> foos = this.processor.lookup(Supplier.class,
				"numbers,foos");
		assertThat(foos.get().blockFirst()).isEqualTo("6");
	}

	@Test
	public void composeUniqueSupplier() {
		this.processor.register(new FunctionRegistration<>(new Source(), "numbers"));
		Supplier<Flux<Integer>> foos = this.processor.lookup(Supplier.class, "");
		assertThat(foos.get().blockFirst()).isEqualTo(3);
	}

	@Test
	public void composeConsumer() {
		this.processor.register(new FunctionRegistration<>(new Foos(), "foos"));
		Sink sink = new Sink();
		this.processor.register(new FunctionRegistration<>(sink, "sink"));
		Function<Flux<Integer>, Mono<Void>> foos = this.processor.lookup(Function.class,
				"foos,sink");
		foos.apply(Flux.just(2)).subscribe();
		assertThat(sink.values).contains("4");
	}

	@Test
	public void composeUniqueConsumer() {
		Sink sink = new Sink();
		this.processor.register(new FunctionRegistration<>(sink, "sink"));
		Function<Flux<String>, Mono<Void>> foos = this.processor.lookup(Function.class,
				"");
		foos.apply(Flux.just("2")).subscribe();
		assertThat(sink.values).contains("2");
	}

	@Test
	public void composeSupplierAndConsumer() {
		AtomicReference<String> ref = new AtomicReference<String>();
		Supplier<String> s = () -> "hello";
		this.processor.register(new FunctionRegistration<>(s, "supplier"));
		Consumer<String> c = x -> ref.set(x.toUpperCase());
		this.processor.register(new FunctionRegistration<>(c, "consumer"));
		Supplier<Mono<Void>> f = this.processor.lookup("supplier|consumer");
		((Mono<Void>) f.get()).block();
		assertThat(ref.get()).isEqualTo("HELLO");
	}

	@Test(expected = IllegalStateException.class)
	public void failComposeSupplierWithMultipleConsumers() {
		AtomicReference<String> ref = new AtomicReference<String>();
		Supplier<String> s = () -> "hello";
		this.processor.register(new FunctionRegistration<>(s, "supplier"));
		Consumer<String> c = x -> ref.set(x.toUpperCase());
		this.processor.register(new FunctionRegistration<>(c, "consumer"));
		Consumer<String> z = x -> ref.set(x.toUpperCase());
		this.processor.register(new FunctionRegistration<>(z, "z"));
		this.processor.lookup("supplier|consumer|z");
	}

	@Test
	public void composeSupplierAndMultipleFunctions() {
		Supplier<String> s = () -> "hello";
		this.processor.register(new FunctionRegistration<>(s, "supplier"));
		Function<String, String> uppercase = x -> x.toUpperCase();
		this.processor.register(new FunctionRegistration<>(uppercase, "uppercase"));
		Function<String, String> concat = x -> x + x;
		this.processor.register(new FunctionRegistration<>(concat, "concat"));

		Supplier<Flux<String>> f = this.processor.lookup("supplier|uppercase|concat");

		assertThat(f.get().blockFirst()).isEqualTo("HELLOHELLO");
	}

	protected static class Source implements Supplier<Integer> {

		@Override
		public Integer get() {
			return 3;
		}

	}

	protected static class Sink implements Consumer<String> {

		private List<String> values = new ArrayList<>();

		@Override
		public void accept(String value) {
			this.values.add(value);
		}

	}

	protected static class Foos implements Function<Integer, String> {

		@Override
		public String apply(Integer t) {
			return "" + 2 * t;
		}

	}

	protected static class Bars implements Function<String, String> {

		@Override
		public String apply(String t) {
			return "Hello " + t;
		}

	}

	protected static class FluxThenMonoFunction
			implements Function<Flux<String>, Mono<Long>> {

		@Override
		public Mono<Long> apply(Flux<String> t) {
			return t.count();
		}

	}

	protected static class MonoThenFluxFunction
			implements Function<Mono<Long>, Flux<Integer>> {

		@Override
		public Flux<Integer> apply(Mono<Long> t) {
			return Flux.range(0, Integer.parseInt(t.block().toString()));
		}

	}

}
