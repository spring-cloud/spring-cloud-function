/*
 * Copyright 2019-present the original author or authors.
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


import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
@SuppressWarnings("unused")
public class FunctionTypeUtilsTests {

	@Test
	public void testDiscoverFunctionalMethod() throws Exception {
		Method method = FunctionTypeUtils.discoverFunctionalMethod(SampleEventConsumer.class);
		assertThat(method.getName()).isEqualTo("accept");
	}

	@Test
	public void testFunctionTypeFrom() throws Exception {
		Type type = FunctionTypeUtils.discoverFunctionTypeFromClass(SimpleConsumer.class);
		//assertThat(type).isInstanceOf(ParameterizedType.class);
		Type wrapperType = FunctionTypeUtils.getInputType(type);
//		Type wrapperType = ((ParameterizedType) type).getActualTypeArguments()[0];
//		assertThat(wrapperType).isInstanceOf(ParameterizedType.class);
		assertThat(wrapperType.getTypeName()).contains("Flux");

		Type innerWrapperType = ((ParameterizedType) wrapperType).getActualTypeArguments()[0];
		assertThat(innerWrapperType).isInstanceOf(ParameterizedType.class);
		assertThat(innerWrapperType.getTypeName()).contains("Message");

		Type targetType = ((ParameterizedType) innerWrapperType).getActualTypeArguments()[0];
		assertThat(targetType).isEqualTo(String.class);
	}

	@Test
	public void testFunctionTypeByClassDiscovery() {
		Type type = FunctionTypeUtils.discoverFunctionTypeFromClass(Function.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getInputType(type))).isAssignableFrom(Object.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(MessageFunction.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getComponentTypeOfInputType(type))).isAssignableFrom(String.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getComponentTypeOfOutputType(type))).isAssignableFrom(String.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(MyMessageFunction.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getComponentTypeOfInputType(type))).isAssignableFrom(String.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getComponentTypeOfOutputType(type))).isAssignableFrom(String.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(MessageConsumer.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getComponentTypeOfInputType(type))).isAssignableFrom(String.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(MyMessageConsumer.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getComponentTypeOfInputType(type))).isAssignableFrom(String.class);
	}

	@Test
	public void testWithComplexHierarchy() {
		Type type = FunctionTypeUtils.discoverFunctionTypeFromClass(ReactiveFunctionImpl.class);
		assertThat(String.class).isAssignableFrom(FunctionTypeUtils.getRawType(FunctionTypeUtils.getComponentTypeOfInputType(type)));
		assertThat(Integer.class).isAssignableFrom(FunctionTypeUtils.getRawType(FunctionTypeUtils.getComponentTypeOfOutputType(type)));
	}

	@Test
	public void testIsTypeCollection() {
		assertThat(FunctionTypeUtils.isTypeCollection(new ParameterizedTypeReference<String>() { }.getType())).isFalse();
		assertThat(FunctionTypeUtils.isTypeCollection(new ParameterizedTypeReference<List<String>>() { }.getType())).isTrue();
		assertThat(FunctionTypeUtils.isTypeCollection(new ParameterizedTypeReference<Flux<List<String>>>() { }.getType())).isTrue();
		assertThat(FunctionTypeUtils.isTypeCollection(new ParameterizedTypeReference<Flux<List<Message<String>>>>() { }.getType())).isTrue();
		assertThat(FunctionTypeUtils.isTypeCollection(new ParameterizedTypeReference<Flux<Message<List<String>>>>() { }.getType())).isFalse();
	}

//	@Test
//	public void testNoNpeFromIsMessage() {
//		FunctionTypeUtilsTests<Date> testService = new FunctionTypeUtilsTests<>();
//
//		Method methodUnderTest =
//			ReflectionUtils.findMethod(testService.getClass(), "notAMessageMethod", AtomicReference.class);
//		MethodParameter methodParameter = MethodParameter.forExecutable(methodUnderTest, 0);
//
//		assertThat(FunctionTypeUtils.isMessage(methodParameter.getGenericParameterType())).isFalse();
//	}

	//@Test
	public void testPrimitiveFunctionInputTypes() {
		Type type = FunctionTypeUtils.discoverFunctionTypeFromClass(IntConsumer.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getInputType(type))).isAssignableFrom(IntConsumer.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(IntSupplier.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getInputType(type))).isAssignableFrom(IntSupplier.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(IntFunction.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getInputType(type))).isAssignableFrom(IntFunction.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(ToIntFunction.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getInputType(type))).isAssignableFrom(ToIntFunction.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(LongConsumer.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getInputType(type))).isAssignableFrom(LongConsumer.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(LongSupplier.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getInputType(type))).isAssignableFrom(LongSupplier.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(LongFunction.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getInputType(type))).isAssignableFrom(LongFunction.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(ToLongFunction.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getInputType(type))).isAssignableFrom(ToLongFunction.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(DoubleConsumer.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getInputType(type))).isAssignableFrom(DoubleConsumer.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(DoubleSupplier.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getInputType(type))).isAssignableFrom(DoubleSupplier.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(DoubleFunction.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getInputType(type))).isAssignableFrom(DoubleFunction.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(ToDoubleFunction.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getInputType(type))).isAssignableFrom(ToDoubleFunction.class);
	}


	//@Test
	public void testPrimitiveFunctionOutputTypes() {
		Type type = FunctionTypeUtils.discoverFunctionTypeFromClass(IntConsumer.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getOutputType(type))).isAssignableFrom(IntConsumer.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(IntSupplier.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getOutputType(type))).isAssignableFrom(IntSupplier.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(IntFunction.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getOutputType(type))).isAssignableFrom(IntFunction.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(ToIntFunction.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getOutputType(type))).isAssignableFrom(ToIntFunction.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(LongConsumer.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getOutputType(type))).isAssignableFrom(LongConsumer.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(LongSupplier.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getOutputType(type))).isAssignableFrom(LongSupplier.class);


		type = FunctionTypeUtils.discoverFunctionTypeFromClass(LongFunction.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getOutputType(type))).isAssignableFrom(LongFunction.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(ToLongFunction.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getOutputType(type))).isAssignableFrom(ToLongFunction.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(DoubleConsumer.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getOutputType(type))).isAssignableFrom(DoubleConsumer.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(DoubleSupplier.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getOutputType(type))).isAssignableFrom(DoubleSupplier.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(DoubleFunction.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getOutputType(type))).isAssignableFrom(DoubleFunction.class);

		type = FunctionTypeUtils.discoverFunctionTypeFromClass(ToDoubleFunction.class);
		assertThat(FunctionTypeUtils.getRawType(FunctionTypeUtils.getOutputType(type))).isAssignableFrom(ToDoubleFunction.class);
	}

//	void notAMessageMethod(AtomicReference<T> payload) {
//
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

	//============

	private interface MessageFunction extends Function<Message<String>, Message<String>> {

	}

	private interface MyMessageFunction extends MessageFunction {

	}

	private interface MessageConsumer extends Consumer<Message<String>> {

	}

	private interface MyMessageConsumer extends MessageConsumer {

	}

	public static class SimpleConsumer implements Consumer<Flux<Message<String>>> {
		@Override
		public void accept(Flux<Message<String>> messageFlux) {
		}
	}

	public interface ReactiveFunction<S, T> extends Function<Flux<S>, Flux<T>> {

	}

	public static class ReactiveFunctionImpl implements ReactiveFunction<String, Integer> {

		@Override
		public Flux<Integer> apply(Flux<String> inFlux) {
			return inFlux.map(v -> Integer.parseInt(v));
		}
	}

	public static abstract class AbstractConsumer<C> implements Consumer<Message<C>> {

		@Override
		public final void accept(Message<C> message) {
			if (message == null) {
				return;
			}

			doAccept(message.getPayload());
		}

		protected abstract void doAccept(C payload);
	}

	public static class SampleEventConsumer extends AbstractConsumer<SampleData> {
		@Override
		protected void doAccept(SampleData data) {
		}
	}

	public static class SampleData {

	}

}
