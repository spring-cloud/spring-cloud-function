/*
 * Copyright 2017 the original author or authors.
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

import java.util.function.Consumer;
import java.util.function.Function;

import reactor.core.publisher.Flux;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cloud.function.context.FunctionInspector;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.converter.CompositeMessageConverterFactory;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConverter;

/**
 * @author Mark Fisher
 * @author Marius Bogoevici
 */
public class StreamListeningConsumerInvoker implements SmartInitializingSingleton {

	private final Consumer<Flux<?>> consumer;

	private final String name;

	private final FunctionInspector functionInspector;

	private final CompositeMessageConverterFactory converterFactory;

	private MessageConverter converter;

	private Class<?> inputType;

	public StreamListeningConsumerInvoker(String name, Consumer<Flux<?>> consumer, FunctionInspector functionInspector,
			CompositeMessageConverterFactory converterFactory) {
		this.consumer = consumer;
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
	public void handle(@Input(Processor.INPUT) Flux<Message<?>> input) {
		this.consumer.accept(input.map(convertInput()));
	}

	private Function<Message<?>, Object> convertInput() {
		return m -> {
			if (this.inputType.isAssignableFrom(m.getPayload().getClass())) {
				return m.getPayload();
			}
			else {
				return converter.fromMessage(m, this.inputType);
			}
		};
	}
}
