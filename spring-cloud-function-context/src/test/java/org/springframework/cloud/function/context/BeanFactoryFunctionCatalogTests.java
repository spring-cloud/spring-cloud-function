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

package org.springframework.cloud.function.context;

import java.util.function.Function;

import org.junit.Test;

import org.springframework.cloud.function.context.ContextFunctionCatalogAutoConfiguration.BeanFactoryFunctionCatalog;
import org.springframework.cloud.function.context.ContextFunctionCatalogAutoConfiguration.ContextFunctionRegistry;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
public class BeanFactoryFunctionCatalogTests {

	private BeanFactoryFunctionCatalog processor = new BeanFactoryFunctionCatalog(
			new ContextFunctionRegistry());

	@Test
	public void basicRegistrationFeatures() {
		processor.register(new FunctionRegistration<>(new Foos()).names("foos"));
		Function<Flux<Integer>, Flux<String>> foos = processor.lookupFunction("foos");
		assertThat(foos.apply(Flux.just(2)).blockFirst()).isEqualTo("4");
	}

	@Test
	public void compose() {
		processor.register(new FunctionRegistration<>(new Foos()).names("foos"));
		processor.register(new FunctionRegistration<>(new Bars()).names("bars"));
		Function<Flux<Integer>, Flux<String>> foos = processor
				.lookupFunction("foos,bars");
		assertThat(foos.apply(Flux.just(2)).blockFirst()).isEqualTo("Hello 4");
	}

	protected static class Foos implements Function<Integer, String> {

		@Override
		public String apply(Integer t) {
			return "" + 2 * t;
		}

	}

	protected static class Bars implements Function<String, String> {

		@Override
		public String apply(String t) {
			return "Hello " + t;
		}

	}

}
