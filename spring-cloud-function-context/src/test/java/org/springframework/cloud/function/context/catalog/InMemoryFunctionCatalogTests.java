/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.context.catalog;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.function.Function;

import org.junit.Test;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.core.FluxFunction;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class InMemoryFunctionCatalogTests {

	@Test
	public void testFunctionRegistration() {
		TestFunction function = new TestFunction();
		FunctionRegistration<TestFunction> registration = new FunctionRegistration<>(function, "foo")
				.type(FunctionType.of(TestFunction.class).getType());
		InMemoryFunctionCatalog catalog = new InMemoryFunctionCatalog();
		catalog.register(registration);
		FunctionRegistration<?> registration2 = catalog.getRegistration(function);
		assertSame(registration, registration2);
	}

	@Test
	public void testFunctionLookup() {
		TestFunction function = new TestFunction();
		FunctionRegistration<TestFunction> registration = new FunctionRegistration<>(function, "foo")
				.type(FunctionType.of(TestFunction.class).getType());
		InMemoryFunctionCatalog catalog = new InMemoryFunctionCatalog();
		catalog.register(registration);

		Object lookedUpFunction = catalog.lookup("hello");
		assertNull(lookedUpFunction);

		lookedUpFunction = catalog.lookup("foo");
		assertNotNull(lookedUpFunction);
		assertTrue(lookedUpFunction instanceof FluxFunction);
	}

	private static class TestFunction implements Function<Integer, String> {
		@Override
		public String apply(Integer t) {
			return "i=" + t;
		}
	}
}
