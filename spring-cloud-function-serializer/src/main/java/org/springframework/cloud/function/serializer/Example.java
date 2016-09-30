/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.cloud.function.serializer;

import java.util.function.Function;

import org.springframework.cloud.function.compiler.CompiledFunctionFactory;
import org.springframework.cloud.function.compiler.FunctionCompiler;
import org.springframework.cloud.function.serializer.jdk.JdkFunctionSerializer;

import reactor.core.publisher.Flux;

/**
 * @author Mark Fisher
 */
public class Example {

	public static void main(String[] args) throws Exception {
		String code = "f->f.map(s->s.toString().toUpperCase())";
		FunctionCompiler compiler = new FunctionCompiler();
		CompiledFunctionFactory<Flux<String>, Flux<String>> functionFactory = compiler.compile(code);

		Function<Flux<String>, Flux<String>> function1 = functionFactory.getFunction();
		Flux<String> result1 = function1.apply(Flux.just("foo", "bar"));
		result1.subscribe(System.out::println);

		JdkFunctionSerializer serializer = new JdkFunctionSerializer();
		byte[] functionBytes = serializer.serialize(function1);
		byte[] factoryBytes = functionFactory.getGeneratedClassBytes();

		Function<Flux<String>, Flux<String>> function2 = serializer.deserialize(functionBytes, factoryBytes);
		Flux<String> result2 = function2.apply(Flux.just("this", "works", "too"));
		result2.subscribe(System.out::println);
	}
}
