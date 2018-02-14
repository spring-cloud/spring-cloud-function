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

package org.springframework.cloud.function.core;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Test;
import org.reactivestreams.Publisher;

import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
public class FunctionFactoryUtilsTests {

	@Test
	public void isFluxConsumer() {
		Method method = ReflectionUtils.findMethod(FunctionFactoryUtilsTests.class, "fluxConsumer");
		assertThat(FunctionFactoryUtils.isFluxConsumer(method)).isTrue();
		assertThat(FunctionFactoryUtils.isFluxSupplier(method)).isFalse();
		assertThat(FunctionFactoryUtils.isFluxFunction(method)).isFalse();
	}
	
	@Test
	public void isFluxSupplier() {
		Method method = ReflectionUtils.findMethod(FunctionFactoryUtilsTests.class, "fluxSupplier");
		assertThat(FunctionFactoryUtils.isFluxSupplier(method)).isTrue();
		assertThat(FunctionFactoryUtils.isFluxConsumer(method)).isFalse();
		assertThat(FunctionFactoryUtils.isFluxFunction(method)).isFalse();
	}

	@Test
	public void isFluxFunction() {
		Method method = ReflectionUtils.findMethod(FunctionFactoryUtilsTests.class, "fluxFunction");
		assertThat(FunctionFactoryUtils.isFluxFunction(method)).isTrue();
		assertThat(FunctionFactoryUtils.isFluxSupplier(method)).isFalse();
		assertThat(FunctionFactoryUtils.isFluxConsumer(method)).isFalse();
	}
	
	@Test
	public void isReactiveFunction() {
		Method method = ReflectionUtils.findMethod(FunctionFactoryUtilsTests.class, "reactiveFunction");
		assertThat(FunctionFactoryUtils.isFluxFunction(method)).isTrue();
		assertThat(FunctionFactoryUtils.isFluxSupplier(method)).isFalse();
		assertThat(FunctionFactoryUtils.isFluxConsumer(method)).isFalse();
	}
	
	public Function<Flux<Foo>, Flux<Foo>> fluxFunction() {
		return foos -> foos.map(foo -> new Foo());
	}

	public Function<Publisher<Foo>, Publisher<Foo>> reactiveFunction() {
		return foos -> Flux.from(foos).map(foo -> new Foo());
	}

	public Supplier<Flux<Foo>> fluxSupplier() {
		return () -> Flux.just(new Foo());
	}

	public Consumer<Flux<Foo>> fluxConsumer() {
		return flux -> flux.subscribe(System.out::println);
	}
	
	class Foo {}

}
