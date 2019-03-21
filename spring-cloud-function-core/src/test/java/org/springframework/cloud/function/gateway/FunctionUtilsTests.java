/*
 * Copyright 2016-2017 the original author or authors.
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

package org.springframework.cloud.function.gateway;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Test;

import org.springframework.cloud.function.support.FunctionUtils;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
public class FunctionUtilsTests {

	@Test
	public void isFluxConsumer() {
		Method method = ReflectionUtils.findMethod(FunctionUtilsTests.class, "fluxConsumer");
		assertThat(FunctionUtils.isFluxConsumer(method)).isTrue();
		assertThat(FunctionUtils.isFluxSupplier(method)).isFalse();
		assertThat(FunctionUtils.isFluxFunction(method)).isFalse();
	}
	
	@Test
	public void isFluxSupplier() {
		Method method = ReflectionUtils.findMethod(FunctionUtilsTests.class, "fluxSupplier");
		assertThat(FunctionUtils.isFluxSupplier(method)).isTrue();
		assertThat(FunctionUtils.isFluxConsumer(method)).isFalse();
		assertThat(FunctionUtils.isFluxFunction(method)).isFalse();
	}

	@Test
	public void isFluxFunction() {
		Method method = ReflectionUtils.findMethod(FunctionUtilsTests.class, "fluxFunction");
		assertThat(FunctionUtils.isFluxFunction(method)).isTrue();
		assertThat(FunctionUtils.isFluxSupplier(method)).isFalse();
		assertThat(FunctionUtils.isFluxConsumer(method)).isFalse();
	}
	
	public Function<Flux<Foo>, Flux<Foo>> fluxFunction() {
		return foos -> foos.map(foo -> new Foo());
	}

	public Supplier<Flux<Foo>> fluxSupplier() {
		return () -> Flux.just(new Foo());
	}

	public Consumer<Flux<Foo>> fluxConsumer() {
		return flux -> flux.subscribe(System.out::println);
	}
	
	class Foo {}

}
