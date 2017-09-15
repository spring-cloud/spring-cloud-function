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

import java.util.function.Consumer;

import org.junit.Test;

import org.springframework.cloud.function.core.FunctionFactoryUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class ConsumerCompilerTests {

	@Test
	public void consumesFluxString() {
		CompiledFunctionFactory<Consumer<String>> compiled = new ConsumerCompiler<String>(
				String.class.getName()).compile("foos",
						"flux -> flux.subscribe(System.out::println)", "Flux<String>");
		assertThat(FunctionFactoryUtils.isFluxConsumer(compiled.getFactoryMethod())).isTrue();
	}

	@Test
	public void consumesString() {
		CompiledFunctionFactory<Consumer<String>> compiled = new ConsumerCompiler<String>(
				String.class.getName()).compile("foos", "System.out::println", "String");
		assertThat(FunctionFactoryUtils.isFluxConsumer(compiled.getFactoryMethod())).isFalse();
	}

}
