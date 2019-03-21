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

package org.springframework.cloud.function.stream;

import java.util.function.Function;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cloud.function.context.FunctionInspector;
import org.springframework.cloud.function.registry.FunctionCatalog;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.converter.CompositeMessageConverterFactory;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;

import reactor.core.publisher.Flux;

/**
 * @author Mark Fisher
 * @author Marius Bogoevici
 */
public class StreamListeningFunctionInvoker implements SmartInitializingSingleton {

	private final FunctionInspector functionInspector;

	private final FunctionCatalog functionCatalog;

	private final CompositeMessageConverterFactory converterFactory;

	private MessageConverter converter;

	private final String defaultEndpoint;

	private final String[] names;

	public StreamListeningFunctionInvoker(FunctionCatalog functionCatalog,
			FunctionInspector functionInspector,
			CompositeMessageConverterFactory converterFactory, String defaultEndpoint,
			String... names) {
		this.functionCatalog = functionCatalog;
		this.functionInspector = functionInspector;
		this.converterFactory = converterFactory;
		this.defaultEndpoint = defaultEndpoint;
		this.names = names;
	}

	@Override
	public void afterSingletonsInstantiated() {
		this.converter = this.converterFactory.getMessageConverterForAllRegistered();
	}

	@StreamListener
	@Output(Processor.OUTPUT)
	public Flux<?> handle(@Input(Processor.INPUT) Flux<Message<?>> input) {
		return input.groupBy(this::select)
				.filter(group -> functionCatalog.lookupFunction(group.key()) != null)
				.flatMap(group -> process(group.key(), group));
	}

	private Flux<?> process(String name, Flux<Message<?>> flux) {
		return (Flux<?>) functionCatalog.lookupFunction(name)
				.apply(flux.map(message -> convertInput(name).apply(message)));
	}

	private String select(Message<?> input) {
		String name = defaultEndpoint;
		if (name == null) {
			for (String candidate : names) {
				Class<?> inputType = functionInspector.getInputType(candidate);
				if (this.converter.fromMessage(input, inputType) != null) {
					name = candidate;
					break;
				}
			}
		}
		return name;
	}

	private Function<Message<?>, Object> convertInput(String name) {
		Class<?> inputType = functionInspector.getInputType(name);
		return m -> {
			if (functionInspector.isMessage(name)) {
				return MessageBuilder.withPayload(convertPayload(name, inputType, m))
						.copyHeaders(m.getHeaders()).build();
			}
			else {
				return convertPayload(name, inputType, m);
			}
		};
	}

	private Object convertPayload(String name, Class<?> inputType, Message<?> m) {
		if (inputType.isAssignableFrom(m.getPayload().getClass())) {
			return m.getPayload();
		}
		else {
			return this.converter.fromMessage(m, inputType);
		}
	}
}
