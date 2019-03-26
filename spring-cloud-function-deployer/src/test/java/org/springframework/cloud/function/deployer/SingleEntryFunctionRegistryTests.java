/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.cloud.function.deployer;

import java.util.function.Function;

import org.junit.Test;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.catalog.InMemoryFunctionCatalog;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class SingleEntryFunctionRegistryTests {

	private InMemoryFunctionCatalog delegate = new InMemoryFunctionCatalog();

	@Test
	public void named() {
		this.delegate.register(new FunctionRegistration<Foos>(new Foos(), "foo"));
		SingleEntryFunctionRegistry registry = new SingleEntryFunctionRegistry(
				this.delegate, "foo");
		assertThat((Foos) registry.lookup("foo")).isInstanceOf(Function.class);
	}

	@Test
	public void other() {
		this.delegate.register(new FunctionRegistration<Foos>(new Foos(), "foo"));
		SingleEntryFunctionRegistry registry = new SingleEntryFunctionRegistry(
				this.delegate, "foo");
		assertThat((Foos) registry.lookup("bar")).isNull();
	}

	@Test
	public void empty() {
		this.delegate.register(new FunctionRegistration<Foos>(new Foos(), ""));
		SingleEntryFunctionRegistry registry = new SingleEntryFunctionRegistry(
				this.delegate, "");
		assertThat((Foos) registry.lookup("")).isInstanceOf(Function.class);
	}

	@Test
	public void anonymous() {
		this.delegate.register(new FunctionRegistration<Foos>(new Foos(), "bar"));
		SingleEntryFunctionRegistry registry = new SingleEntryFunctionRegistry(
				this.delegate, "foo");
		assertThat((Foos) registry.lookup("")).isInstanceOf(Function.class);
	}

	class Foos implements Function<String, String> {

		@Override
		public String apply(String t) {
			return t.toUpperCase();
		}

	}

}
