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

package org.springframework.cloud.function.stream;

import java.util.function.Function;

import org.springframework.cloud.function.invoker.AbstractFunctionInvoker;
import org.springframework.cloud.function.support.FluxFunction;
import org.springframework.cloud.function.support.FunctionUtils;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.util.Assert;

import reactor.core.publisher.Flux;

/**
 * @author Mark Fisher
 */
public class StreamListeningFunctionInvoker extends AbstractFunctionInvoker<Flux<?>, Flux<?>> {

	public StreamListeningFunctionInvoker(Function<?, ?> function) {
		super(wrapIfNecessary(function));
	}

	@StreamListener
	@Output(Processor.OUTPUT)
	public Flux<?> handle(@Input(Processor.INPUT) Flux<?> input) {
		return this.doInvoke(input);
	}

	private static Function<Flux<?>, Flux<?>> wrapIfNecessary(Function function) {
		Assert.notNull(function, "Function must not be null");
		if (!FunctionUtils.isFluxFunction(function)) {
			function = new FluxFunction(function);
		}
		return function;
	}
}
