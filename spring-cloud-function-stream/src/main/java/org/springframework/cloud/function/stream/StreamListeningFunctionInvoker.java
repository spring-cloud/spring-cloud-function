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

import reactor.core.publisher.Flux;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cloud.function.context.FunctionInspector;
import org.springframework.cloud.function.invoker.AbstractFunctionInvoker;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.converter.CompositeMessageConverterFactory;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConverter;

/**
 * @author Mark Fisher
 * @author Marius Bogoevici
 */
public class StreamListeningFunctionInvoker extends AbstractFunctionInvoker<Flux<?>, Flux<?>>
		implements SmartInitializingSingleton {

	private final String name;

	private final FunctionInspector functionInspector;

	private final CompositeMessageConverterFactory converterFactory;

	private MessageConverter converter;

	private Class<?> inputType;

	public StreamListeningFunctionInvoker(String name, Function<Flux<?>, Flux<?>> function, FunctionInspector functionInspector,
			CompositeMessageConverterFactory converterFactory) {
		super(function);
		this.name = name;
		this.functionInspector = functionInspector;
		this.converterFactory = converterFactory;
	}

	@Override
	public void afterSingletonsInstantiated() {
		this.converter = this.converterFactory.getMessageConverterForAllRegistered();
		this.inputType = this.functionInspector.getInputType(this.name);
	}

	@StreamListener
	@Output(Processor.OUTPUT)
	public Flux<?> handle(@Input(Processor.INPUT) Flux<Message<?>> input) {
		return this.doInvoke(input.map(convertInput()));
	}

	private Function<Message<?>, Object> convertInput() {
		return m -> {
			if (this.inputType.isAssignableFrom(m.getPayload().getClass())) {
				return m.getPayload();
			}
			else {
				return this.converter.fromMessage(m, this.inputType);
			}
		};
	}
}
