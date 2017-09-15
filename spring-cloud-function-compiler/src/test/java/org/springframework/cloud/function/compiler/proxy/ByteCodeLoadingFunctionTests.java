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

package org.springframework.cloud.function.compiler.proxy;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Test;

import org.springframework.cloud.function.compiler.CompiledFunctionFactory;
import org.springframework.cloud.function.compiler.ConsumerCompiler;
import org.springframework.cloud.function.compiler.FunctionCompiler;
import org.springframework.cloud.function.compiler.SupplierCompiler;
import org.springframework.cloud.function.core.FunctionFactoryMetadata;
import org.springframework.cloud.function.core.FunctionFactoryUtils;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class ByteCodeLoadingFunctionTests {

	@Test
	public void compileConsumer() throws Exception {
		CompiledFunctionFactory<Consumer<String>> compiled = new ConsumerCompiler<String>(
				String.class.getName()).compile("foos", "System.out::println", "String");
		ByteArrayResource resource = new ByteArrayResource(compiled.getGeneratedClassBytes(), "foos");
		ByteCodeLoadingConsumer<String> consumer = new ByteCodeLoadingConsumer<>(resource);
		consumer.afterPropertiesSet();
		assertThat(consumer instanceof FunctionFactoryMetadata);
		assertThat(FunctionFactoryUtils.isFluxConsumer(consumer.getFactoryMethod())).isFalse();
		consumer.accept("foo");
	}

	@Test
	public void compileSupplier() throws Exception {
		CompiledFunctionFactory<Supplier<String>> compiled = new SupplierCompiler<String>(
				String.class.getName()).compile("foos", "() -> \"foo\"", "String");
		ByteArrayResource resource = new ByteArrayResource(compiled.getGeneratedClassBytes(), "foos");
		ByteCodeLoadingSupplier<String> supplier = new ByteCodeLoadingSupplier<>(resource);
		supplier.afterPropertiesSet();
		assertThat(supplier instanceof FunctionFactoryMetadata);
		assertThat(FunctionFactoryUtils.isFluxSupplier(supplier.getFactoryMethod())).isFalse();
		assertThat(supplier.get()).isEqualTo("foo");
	}

	@Test
	public void compileFunction() throws Exception {
		CompiledFunctionFactory<Function<String, String>> compiled = new FunctionCompiler<String, String>(
				String.class.getName()).compile("foos", "v -> v.toUpperCase()", "String", "String");
		ByteArrayResource resource = new ByteArrayResource(compiled.getGeneratedClassBytes(), "foos");
		ByteCodeLoadingFunction<String, String> function = new ByteCodeLoadingFunction<>(resource);
		function.afterPropertiesSet();
		assertThat(function instanceof FunctionFactoryMetadata);
		assertThat(FunctionFactoryUtils.isFluxFunction(function.getFactoryMethod())).isFalse();
		assertThat(function.apply("foo")).isEqualTo("FOO");
	}

	@Test
	public void compileFluxFunction() throws Exception {
		CompiledFunctionFactory<Function<Flux<String>, Flux<String>>> compiled = new FunctionCompiler<Flux<String>, Flux<String>>(
				String.class.getName()).compile("foos", "flux -> flux.map(v -> v.toUpperCase())", "Flux<String>", "Flux<String>");
		ByteArrayResource resource = new ByteArrayResource(compiled.getGeneratedClassBytes(), "foos");
		ByteCodeLoadingFunction<Flux<String>, Flux<String>> function = new ByteCodeLoadingFunction<>(resource);
		function.afterPropertiesSet();
		assertThat(function instanceof FunctionFactoryMetadata);
		assertThat(FunctionFactoryUtils.isFluxFunction(function.getFactoryMethod())).isTrue();
		assertThat(function.apply(Flux.just("foo")).blockFirst()).isEqualTo("FOO");
	}
}
