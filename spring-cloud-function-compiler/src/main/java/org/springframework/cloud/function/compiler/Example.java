/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.cloud.function.compiler;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import reactor.core.publisher.Flux;

/**
 * @author Mark Fisher
 */
public class Example {

	public static void main(String[] args) {
		SupplierCompiler<Flux<String>> supplierCompiler = new SupplierCompiler<>();
		CompiledFunctionFactory<Supplier<Flux<String>>> supplierFactory = supplierCompiler.compile("s", "return ()->Flux.just(\"foo\");");
		Flux<String> input = supplierFactory.getResult().get();

		FunctionCompiler<Flux<String>, Flux<String>> functionCompiler = new FunctionCompiler<>();
		CompiledFunctionFactory<Function<Flux<String>,Flux<String>>> functionFactory = functionCompiler.compile("f", "f->f.map(s->s.toString().toUpperCase())");
		Flux<String> output = functionFactory.getResult().apply(input);

		ConsumerCompiler<String> consumerCompiler = new ConsumerCompiler<>();
		CompiledFunctionFactory<Consumer<String>> consumerFactory = consumerCompiler.compile("c", "System.out::println");
		output.subscribe(consumerFactory.getResult());
	}
}
