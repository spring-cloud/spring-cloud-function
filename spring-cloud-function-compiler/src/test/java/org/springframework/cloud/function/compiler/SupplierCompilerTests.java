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

package org.springframework.cloud.function.compiler;

import java.util.function.Supplier;

import org.junit.Test;
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.core.FunctionFactoryUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class SupplierCompilerTests {

	@Test
	public void supppliesFluxString() {
		CompiledFunctionFactory<Supplier<String>> compiled = new SupplierCompiler<String>(
				String.class.getName()).compile("foos",
						"() -> Flux.just(\"foo\", \"bar\")", "Flux<String>");
		assertThat(FunctionFactoryUtils.isFluxSupplier(compiled.getFactoryMethod())).isTrue();
	}

	@Test
	public void supppliesString() {
		CompiledFunctionFactory<Supplier<String>> compiled = new SupplierCompiler<String>(
				String.class.getName()).compile("foos",
						"() -> \"foo\"", "String");
		assertThat(FunctionFactoryUtils.isFluxSupplier(compiled.getFactoryMethod())).isFalse();
		assertThat(compiled.getResult().get()).isEqualTo("foo");
	}

	@Test
	public void supppliesFluxStreamString() {
		CompiledFunctionFactory<Supplier<Flux<String>>> compiled = new SupplierCompiler<Flux<String>>(
				String.class.getName()).compile("foos",
				"() -> Flux.interval(Duration.ofMillis(1000)).map(Object::toString)",
				"Flux<String>");
		assertThat(FunctionFactoryUtils.isFluxSupplier(compiled.getFactoryMethod())).isTrue();
		assertThat(compiled.getResult().get().blockFirst()).isEqualTo("0");
	}
}
