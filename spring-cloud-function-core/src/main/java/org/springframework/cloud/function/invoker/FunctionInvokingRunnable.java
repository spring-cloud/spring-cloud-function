/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.cloud.function.invoker;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Mark Fisher
 *
 * @param <T> output of supplier, input to function
 * @param <R> output of function, input to consumer 
 */
public class FunctionInvokingRunnable<T, R> implements Runnable {

	private final Supplier<T> supplier;

	private final Function<T, R> function;

	private final Consumer<R> consumer;

	public FunctionInvokingRunnable(Supplier<T> supplier, Function<T, R> function, Consumer<R> consumer) {
		this.supplier = supplier;
		this.function = function;
		this.consumer = consumer;
	}

	@Override
	public void run() {
		this.consumer.accept(this.function.apply(this.supplier.get()));
	}
}
