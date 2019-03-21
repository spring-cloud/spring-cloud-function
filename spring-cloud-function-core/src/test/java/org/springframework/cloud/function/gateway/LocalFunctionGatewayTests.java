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

package org.springframework.cloud.function.gateway;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Before;
import org.junit.Test;

import org.springframework.cloud.function.registry.FunctionCatalog;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import reactor.core.publisher.Flux;

/**
 * @author Mark Fisher
 */
public class LocalFunctionGatewayTests {

	private final FunctionCatalog catalog = new FunctionCatalog() {

		@Override
		public <T> Supplier<T> lookupSupplier(String name) {
			return null;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Function<Flux<String>, Flux<String>> lookupFunction(String name) {
			return ("uppercase".equals(name) ? f->f.map(s->s.toString().toUpperCase()) : null);
		}

		@Override
		public <T> Consumer<T> lookupConsumer(String name) {
			return null;
		}
	};

	private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

	@Before
	public void init() {
		this.scheduler.initialize();
	}

	@Test
	public void test() {
		LocalFunctionGateway gateway = new LocalFunctionGateway(catalog, scheduler);
		Flux<String> output = gateway.invoke("uppercase", Flux.just("foo", "bar"));
		List<String> results = output.collectList().block();
		assertEquals("FOO", results.get(0));
		assertEquals("BAR", results.get(1));
	}

	@Test
	public void testMultiple() {
		for (int i = 0; i < 100; i++) {
			test();
		}
	}
}
