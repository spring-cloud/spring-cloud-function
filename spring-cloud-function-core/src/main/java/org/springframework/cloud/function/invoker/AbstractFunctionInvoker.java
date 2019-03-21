/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.invoker;

import java.util.function.Function;

import org.springframework.util.Assert;

/**
 * @author Mark Fisher
 *
 * @param <T> function parameter type
 * @param <R> function return type
 */
public abstract class AbstractFunctionInvoker<T, R> {

	private final Function<T, R> function;

	protected AbstractFunctionInvoker(Function<T, R> function) {
		Assert.notNull(function, "Function must not be null");
		this.function = function;
	}

	protected R doInvoke(T input) {
		return this.function.apply(input);
	}
}
