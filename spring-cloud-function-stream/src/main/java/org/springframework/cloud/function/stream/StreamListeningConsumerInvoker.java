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

import java.util.Set;
import java.util.function.Function;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cloud.function.context.FunctionInspector;
import org.springframework.cloud.function.registry.FunctionCatalog;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.converter.CompositeMessageConverterFactory;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConverter;

import reactor.core.publisher.Flux;

/**
 * @author Mark Fisher
 * @author Marius Bogoevici
 */
public class StreamListeningConsumerInvoker implements SmartInitializingSingleton {

	private final FunctionInspector functionInspector;

	private final CompositeMessageConverterFactory converterFactory;

	private MessageConverter converter;

	private final FunctionCatalog functionCatalog;

	private final String defaultEndpoint;

	private static final String NOENDPOINT = "__NOENDPOINT__";

	public StreamListeningConsumerInvoker(FunctionCatalog functionCatalog,
			FunctionInspector functionInspector,
			CompositeMessageConverterFactory converterFactory, String defaultEndpoint) {
		this.functionCatalog = functionCatalog;
		this.functionInspector = functionInspector;
		this.converterFactory = converterFactory;
		this.defaultEndpoint = defaultEndpoint;
	}

	@Override
	public void afterSingletonsInstantiated() {
		this.converter = this.converterFactory.getMessageConverterForAllRegistered();
	}

	@StreamListener
	public void handle(@Input(Sink.INPUT) Flux<Message<?>> input) {
		input.groupBy(this::select)
				.filter(group -> functionCatalog.lookupConsumer(group.key()) != null)
				.subscribe(group -> process(group.key(), group));
	}

	private void process(String name, Flux<Message<?>> flux) {
		functionCatalog.lookupConsumer(name)
				.accept(flux.map(message -> convertInput(name).apply(message)));
	}

	private String select(Message<?> input) {
		String name = defaultEndpoint;
		if (name == null) {
			Set<String> names = functionCatalog.getConsumerNames();
			if (names.size() == 1) {
				name = names.iterator().next();
			}
			else {
				if (input.getHeaders()
						.containsKey(StreamConfigurationProperties.ROUTE_KEY)) {
					String key = (String) input.getHeaders()
							.get(StreamConfigurationProperties.ROUTE_KEY);
					if (functionCatalog.lookupConsumer(key) != null) {
						return key;
					}
				}
				else {
					for (String candidate : names) {
						Class<?> inputType = functionInspector
								.getInputType(functionCatalog.lookupConsumer(candidate));
						Object value = this.converter.fromMessage(input, inputType);
						if (value != null && inputType.isInstance(value)) {
							name = candidate;
							break;
						}
					}
				}
			}
		}
		if (name == null) {
			return NOENDPOINT;
		}
		return name;
	}

	private Function<Message<?>, Object> convertInput(String name) {
		Class<?> inputType = functionInspector
				.getInputType(functionCatalog.lookupConsumer(name));
		return m -> {
			if (Message.class.isAssignableFrom(inputType)) {
				return m;
			}
			else if (inputType.isAssignableFrom(m.getPayload().getClass())) {
				return m.getPayload();
			}
			else {
				return this.converter.fromMessage(m, inputType);
			}
		};
	}
}
