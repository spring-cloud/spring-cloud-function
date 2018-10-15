/*
 * Copyright 2016-2018 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration.ContextFunctionRegistry;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ClassUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Dave Syer
 *
 */
// TODO: test all sorts of error conditions (duplicate registrations, incompatible types
// for functions with the same name, uncomposable combinations)
public class ContextFunctionPostProcessorTests {

	private ContextFunctionRegistry processor = new ContextFunctionRegistry();
	private URLClassLoader classLoader;
	private ClassLoader contextClassLoader;

	@After
	public void close() throws Exception {
		if (this.classLoader != null) {
			this.classLoader.close();
		}
		if (Thread.currentThread().getContextClassLoader() != null) {
			ClassUtils.overrideThreadContextClassLoader(contextClassLoader);
		}
	}

	@Test
	public void basicRegistrationFeatures() {
		processor.register(new FunctionRegistration<>(new Foos(), "foos"));
		@SuppressWarnings("unchecked")
		Function<Flux<Integer>, Flux<String>> foos = (Function<Flux<Integer>, Flux<String>>) processor
				.lookupFunction("foos");
		assertThat(foos.apply(Flux.just(2)).blockFirst()).isEqualTo("4");
	}

	@Test
	public void registrationThroughMerge() {
		FunctionRegistration<Foos> registration = new FunctionRegistration<>(new Foos(), "foos");
		processor.merge(Collections.singletonMap("foos", registration),
				Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
		@SuppressWarnings("unchecked")
		Function<Flux<Integer>, Flux<String>> foos = (Function<Flux<Integer>, Flux<String>>) processor
				.lookupFunction("foos");
		assertThat(foos.apply(Flux.just(2)).blockFirst()).isEqualTo("4");
	}

	@Test
	public void registrationThroughMergeFromNamedFunction() {
		processor.merge(Collections.emptyMap(), Collections.emptyMap(),
				Collections.emptyMap(), Collections.singletonMap("foos", new Foos()));
		@SuppressWarnings("unchecked")
		Function<Flux<Integer>, Flux<String>> foos = (Function<Flux<Integer>, Flux<String>>) processor
				.lookupFunction("foos");
		assertThat(foos.apply(Flux.just(2)).blockFirst()).isEqualTo("4");
	}

	@Test
	public void composeWithComma() {
		processor.register(new FunctionRegistration<>(new Foos(), "foos"));
		processor.register(new FunctionRegistration<>(new Bars(), "bars"));
		@SuppressWarnings("unchecked")
		Function<Flux<Integer>, Flux<String>> foos = (Function<Flux<Integer>, Flux<String>>) processor
				.lookupFunction("foos,bars");
		assertThat(foos.apply(Flux.just(2)).blockFirst()).isEqualTo("Hello 4");
		assertThat(processor.getRegistration(foos).getNames())
				.containsExactly("foos|bars");
	}

	@Test
	public void supplierAndFunction() {
		processor.register(new FunctionRegistration<Supplier<String>>(() -> "foo", "supplier"));
		processor.register(new FunctionRegistration<Function<String, String>>((x) -> x.toUpperCase(), "function"));
		@SuppressWarnings("unchecked")
		Supplier<Flux<String>> supplier = (Supplier<Flux<String>>) processor.lookupSupplier("supplier|function");
		assertThat(supplier.get().blockFirst()).isEqualTo("FOO");
		assertThat(processor.getRegistration(supplier).getNames()).containsExactly("supplier|function");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void supplierAndConsumer() {
		processor.register(new FunctionRegistration<Supplier<String>>(() -> "foo", "supplier"));
		processor.register(new FunctionRegistration<Consumer<String>>(System.out::println, "consumer"));
		Supplier<Mono<Void>> supplier = (Supplier<Mono<Void>>) processor.lookupSupplier("supplier|consumer");
		assertNull(supplier.get().block());
	}

	@Test
	public void compose() {
		processor.register(new FunctionRegistration<>(new Foos(), "foos"));
		processor.register(new FunctionRegistration<>(new Bars(), "bars"));
		@SuppressWarnings("unchecked")
		Function<Flux<Integer>, Flux<String>> foos = (Function<Flux<Integer>, Flux<String>>) processor
				.lookupFunction("foos|bars");
		assertThat(foos.apply(Flux.just(2)).blockFirst()).isEqualTo("Hello 4");
		assertThat(processor.getRegistration(foos).getNames())
				.containsExactly("foos|bars");
	}

	@Test
	public void composeWrapper() {
		processor.register(new FunctionRegistration<>(new WrappedSource(), "ints"));
		processor.register(new FunctionRegistration<>(new Foos(), "foos"));
		@SuppressWarnings("unchecked")
		Supplier<Flux<String>> foos = (Supplier<Flux<String>>) processor
				.lookupSupplier("ints|foos");
		assertThat(foos.get().blockFirst()).isEqualTo("8");
		assertThat(processor.getRegistration(foos).getNames())
				.containsExactly("ints|foos");
		assertThat(processor.getRegistration(foos).getType().getOutputWrapper())
				.isEqualTo(Flux.class);
	}

	@Test
	public void isolatedFunction() {
		contextClassLoader = ClassUtils
				.overrideThreadContextClassLoader(getClass().getClassLoader());
		processor.register(new FunctionRegistration<>(create(Foos.class), "foos"));
		@SuppressWarnings("unchecked")
		Function<Flux<Integer>, Flux<String>> foos = (Function<Flux<Integer>, Flux<String>>) processor
				.lookupFunction("foos");
		assertThat(foos.apply(Flux.just(2)).blockFirst()).isEqualTo("4");
	}

	@Test
	public void isolatedSupplier() {
		contextClassLoader = ClassUtils
				.overrideThreadContextClassLoader(getClass().getClassLoader());
		processor.register(
				new FunctionRegistration<>(create(Source.class), "source"));
		@SuppressWarnings("unchecked")
		Supplier<Flux<Integer>> source = (Supplier<Flux<Integer>>) processor
				.lookupSupplier("source");
		assertThat(source.get().blockFirst()).isEqualTo(4);
	}

	@Test
	public void isolatedConsumer() {
		contextClassLoader = ClassUtils
				.overrideThreadContextClassLoader(getClass().getClassLoader());
		Object target = create(Sink.class);
		processor.register(new FunctionRegistration<>(target, "sink"));
		@SuppressWarnings("unchecked")
		Function<Flux<String>, Mono<Void>> sink = (Function<Flux<String>, Mono<Void>>) processor
				.lookupFunction("sink");
		sink.apply(Flux.just("Hello")).subscribe();
		@SuppressWarnings("unchecked")
		List<String> values = (List<String>) ReflectionTestUtils.getField(target,
				"values");
		assertThat(values).contains("Hello");
	}

	private Object create(Class<?> type) {
		// Want to load these the test types in a disposable classloader:
		List<URL> urls = new ArrayList<>();
		String jcp = System.getProperty("java.class.path");
		StringTokenizer jcpEntries = new StringTokenizer(jcp, File.pathSeparator);
		while (jcpEntries.hasMoreTokens()) {
			String pathEntry = jcpEntries.nextToken();
			try {
				urls.add(new File(pathEntry).toURI().toURL());
			} catch (MalformedURLException e) {
			}
		}
		this.classLoader = new URLClassLoader(
				urls.toArray(new URL[0]),
				getClass().getClassLoader().getParent());
		return BeanUtils
				.instantiateClass(ClassUtils.resolveClassName(type.getName(), classLoader));
	}

	public static class Foos implements Function<Integer, String> {

		@Override
		public String apply(Integer t) {
			assertThat(ClassUtils.resolveClassName(Bar.class.getName(), null)
					.getClassLoader()).isEqualTo(getClass().getClassLoader());
			return "" + 2 * t;
		}

	}

	public static class Bars implements Function<String, String> {

		@Override
		public String apply(String t) {
			assertThat(ClassUtils.resolveClassName(Bar.class.getName(), null)
					.getClassLoader()).isEqualTo(getClass().getClassLoader());
			return "Hello " + t;
		}

	}

	public static class Sink implements Consumer<String> {

		private List<String> values = new ArrayList<>();

		@Override
		public void accept(String t) {
			assertThat(ClassUtils.resolveClassName(Bar.class.getName(), null)
					.getClassLoader()).isEqualTo(getClass().getClassLoader());
			values.add(t);
		}

	}

	public static class Source implements Supplier<Integer> {

		@Override
		public Integer get() {
			return 4;
		}

	}

	public static class WrappedSource implements Supplier<Flux<Integer>> {

		@Override
		public Flux<Integer> get() {
			return Flux.just(4);
		}

	}

	public static class Foo {
		private String value;

		public Foo(String value) {
			this.value = value;
		}

		Foo() {
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

	public static class Bar {
		private String message;

		public Bar(String value) {
			this.message = value;
		}

		Bar() {
		}

		public String getMessage() {
			return this.message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

	}

}
