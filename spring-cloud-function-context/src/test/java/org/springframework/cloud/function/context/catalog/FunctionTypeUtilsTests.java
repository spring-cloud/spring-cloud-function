/*
 * Copyright 2019-2019 the original author or authors.
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

package org.springframework.cloud.function.context.catalog;


import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
@SuppressWarnings("unused")
public class FunctionTypeUtilsTests {

	@Test
	public void testInputCount() throws Exception {
		int inputCount = FunctionTypeUtils.getInputCount(getReturnType("function"));
		assertThat(inputCount).isEqualTo(1);
		inputCount = FunctionTypeUtils.getInputCount(getReturnType("typelessFunction"));
		assertThat(inputCount).isEqualTo(1);
		inputCount = FunctionTypeUtils.getInputCount(getReturnType("multiInputOutputFunction"));
		assertThat(inputCount).isEqualTo(2);
		inputCount = FunctionTypeUtils.getInputCount(getReturnType("multiInputOutputPublisherFunction"));
		assertThat(inputCount).isEqualTo(2);
		inputCount = FunctionTypeUtils.getInputCount(getReturnType("multiInputOutputPublisherFunctionComplexTypes"));
		assertThat(inputCount).isEqualTo(2);
		inputCount = FunctionTypeUtils.getInputCount(getReturnType("consumer"));
		assertThat(inputCount).isEqualTo(1);
		inputCount = FunctionTypeUtils.getInputCount(getReturnType("typelessConsumer"));
		assertThat(inputCount).isEqualTo(1);
		inputCount = FunctionTypeUtils.getInputCount(getReturnType("multiInputConsumer"));
		assertThat(inputCount).isEqualTo(2);
		inputCount = FunctionTypeUtils.getInputCount(getReturnType("supplier"));
		assertThat(inputCount).isEqualTo(0);
		inputCount = FunctionTypeUtils.getInputCount(getReturnType("typelessSupplier"));
		assertThat(inputCount).isEqualTo(0);
	}

	@Test
	public void testOutputCount() throws Exception {
		int outputCount = FunctionTypeUtils.getOutputCount(getReturnType("function"));
		assertThat(outputCount).isEqualTo(1);
		outputCount = FunctionTypeUtils.getOutputCount(getReturnType("typelessFunction"));
		assertThat(outputCount).isEqualTo(1);
		outputCount = FunctionTypeUtils.getOutputCount(getReturnType("multiInputOutputFunction"));
		assertThat(outputCount).isEqualTo(3);
		outputCount = FunctionTypeUtils.getOutputCount(getReturnType("multiInputOutputPublisherFunction"));
		assertThat(outputCount).isEqualTo(3);
		outputCount = FunctionTypeUtils.getOutputCount(getReturnType("multiInputOutputPublisherFunctionComplexTypes"));
		assertThat(outputCount).isEqualTo(3);
		outputCount = FunctionTypeUtils.getOutputCount(getReturnType("consumer"));
		assertThat(outputCount).isEqualTo(0);
		outputCount = FunctionTypeUtils.getOutputCount(getReturnType("typelessConsumer"));
		assertThat(outputCount).isEqualTo(0);
		outputCount = FunctionTypeUtils.getOutputCount(getReturnType("multiInputConsumer"));
		assertThat(outputCount).isEqualTo(0);
		outputCount = FunctionTypeUtils.getOutputCount(getReturnType("supplier"));
		assertThat(outputCount).isEqualTo(1);
		outputCount = FunctionTypeUtils.getOutputCount(getReturnType("typelessSupplier"));
		assertThat(outputCount).isEqualTo(1);
		outputCount = FunctionTypeUtils.getOutputCount(getReturnType("multiOutputSupplier"));
		assertThat(outputCount).isEqualTo(2);
	}

//	@Test
//	public void testInputTypeByIndex() throws Exception {
//		Type functionType = getReturnType("function");
//		Type inputType = FunctionTypeUtils.getInputType(functionType, 0);
//		assertThat(inputType.getTypeName()).isEqualTo(String.class.getName());
//		inputType = FunctionTypeUtils.getInputType(functionType, 1);
//		assertThat(inputType.getTypeName()).isEqualTo(Integer.class.getName());
//
//		functionType = getReturnType("multiInputOutputPublisherFunction");
//		inputType = FunctionTypeUtils.getInputType(functionType, 0);
//		System.out.println("Reactive: " + FunctionTypeUtils.isReactive(inputType));
//		System.out.println("Reactive: " + FunctionTypeUtils.getPublisherType(inputType));
//		System.out.println("Reactive: " + FunctionTypeUtils.getImmediateGenericType(inputType, 0));
//		System.out.println(inputType);
//
//		functionType = getReturnType("typelessFunction");
//		inputType = FunctionTypeUtils.getInputType(functionType, 0);
//		System.out.println(inputType);
//	}


	private static Function<String, Integer> function() {
		return null;
	}

	@SuppressWarnings("rawtypes")
	private static Function typelessFunction() {
		return null;
	}

	private static Function<Tuple2<String, String>, Tuple3<String, Integer, String>> multiInputOutputFunction() {
		return null;
	}

	private static Function<Tuple2<Flux<String>, Mono<String>>,
			Tuple3<Flux<String>, Flux<String>, Mono<String>>> multiInputOutputPublisherFunction() {
		return null;
	}

	private static Function<Tuple2<Flux<Map<String, Integer>>, Mono<String>>,
			Tuple3<Flux<List<byte[]>>, Flux<String>, Mono<String>>> multiInputOutputPublisherFunctionComplexTypes() {
		return null;
	}

	private static Consumer<String> consumer() {
		return null;
	}

	private static Consumer<Tuple2<String, String>> multiInputConsumer() {
		return null;
	}

	@SuppressWarnings("rawtypes")
	private static Consumer typelessConsumer() {
		return null;
	}

	private static Supplier<String> supplier() {
		return null;
	}

	@SuppressWarnings("rawtypes")
	private static Supplier typelessSupplier() {
		return null;
	}

	private static Supplier<Tuple2<String, String>> multiOutputSupplier() {
		return null;
	}

	private Type getReturnType(String methodName) throws Exception {
		return FunctionTypeUtilsTests.class.getDeclaredMethod(methodName).getGenericReturnType();
	}
}
