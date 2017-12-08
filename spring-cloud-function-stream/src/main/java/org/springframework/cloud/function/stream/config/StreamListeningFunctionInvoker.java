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

package org.springframework.cloud.function.stream.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.core.FunctionCatalog;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.converter.CompositeMessageConverterFactory;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.cloud.stream.reactive.FluxSender;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Mark Fisher
 * @author Marius Bogoevici
 */
public class StreamListeningFunctionInvoker implements SmartInitializingSingleton {

	private final FunctionInspector functionInspector;

	private final FunctionCatalog functionCatalog;

	private final CompositeMessageConverterFactory converterFactory;

	private MessageConverter converter;

	private final String defaultRoute;

	private final Map<String, FluxMessageProcessor> processors = new HashMap<>();

	private int count = -1;

	private static final FluxMessageProcessor NOENDPOINT = flux -> Flux.empty();

	private static final Object UNCONVERTED = new Object();

	public StreamListeningFunctionInvoker(FunctionCatalog functionCatalog,
			FunctionInspector functionInspector,
			CompositeMessageConverterFactory converterFactory, String defaultRoute) {
		this.functionCatalog = functionCatalog;
		this.functionInspector = functionInspector;
		this.converterFactory = converterFactory;
		this.defaultRoute = defaultRoute;
	}

	@Override
	public void afterSingletonsInstantiated() {
		this.converter = this.converterFactory.getMessageConverterForAllRegistered();
	}

	@StreamListener
	public Mono<Void> handle(@Input(Processor.INPUT) Flux<Message<?>> input,
			@Output(Processor.OUTPUT) FluxSender output) {
		return output.send(
				input.groupBy(this::select).flatMap(group -> group.key().process(group)));
	}

	private Flux<Message<?>> function(String name, Flux<Message<?>> flux) {
		Function<Object, Flux<?>> function = functionCatalog.lookupFunction(name);
		return flux.publish(values -> {
			Flux<?> result = function
					.apply(values.map(message -> convertInput(function).apply(message)));
			Flux<Map<String, Object>> aggregate = headers(values);
			return result.withLatestFrom(aggregate, (p, m) -> message(p, m));
		});
	}

	private Flux<Map<String, Object>> headers(Flux<Message<?>> flux) {
		return flux.map(message -> message.getHeaders());
	}

	private Message<?> message(Object result, Map<String, Object> headers) {
		return result instanceof Message
				// TODO: why do we have to do this? The headers should have come with the
				// result.
				? MessageBuilder.fromMessage((Message<?>) result)
						.copyHeadersIfAbsent(headers).build()
				: MessageBuilder.withPayload(result).copyHeadersIfAbsent(headers).build();
	}

	private Flux<Message<?>> consumer(String name, Flux<Message<?>> flux) {
		Consumer<Object> consumer = functionCatalog.lookupConsumer(name);
		consumer.accept(flux.map(message -> convertInput(consumer).apply(message))
				.filter(transformed -> transformed != UNCONVERTED));
		return Flux.empty();
	}

	private Flux<Message<?>> balance(List<String> names, Flux<Message<?>> flux) {
		if (names.isEmpty()) {
			return Flux.empty();
		}
		String name = choose(names);
		if (functionCatalog.lookupConsumer(name) != null) {
			return consumer(name, flux);
		}
		return function(name, flux);
	}

	private synchronized String choose(List<String> names) {
		if (++count >= names.size() || count < 0) {
			count = 0;
		}
		return names.get(count);
	}

	private FluxMessageProcessor select(Message<?> input) {
		String name = null;
		if (input.getHeaders().containsKey(StreamConfigurationProperties.ROUTE_KEY)) {
			String key = (String) input.getHeaders()
					.get(StreamConfigurationProperties.ROUTE_KEY);
			name = stash(key);
		}
		if (name==null && defaultRoute != null) {
			name = stash(defaultRoute);
		}
		if (name == null) {
			Set<String> names = new LinkedHashSet<>(functionCatalog.getFunctionNames());
			names.addAll(functionCatalog.getConsumerNames());
			List<String> matches = new ArrayList<>();
			if (names.size() == 1) {
				String key = names.iterator().next();
				name = stash(key);
			}
			else {
				for (String candidate : names) {
					Object function = functionCatalog.lookupFunction(candidate);
					if (function==null) {
						function = functionCatalog.lookupConsumer(candidate);
					}
					if (function==null) {
						continue;
					}
					Class<?> inputType = functionInspector.getInputType(function);
					Object value = this.converter.fromMessage(input, inputType);
					if (value != null && inputType.isInstance(value)) {
						matches.add(candidate);
					}
				}
				if (matches.size() == 1) {
					name = stash(matches.iterator().next());
				}
				else {
					// TODO: do we really want this? Or maybe warn that it is happening?
					return flux -> balance(matches, flux);
				}
			}
		}
		if (name == null) {
			return NOENDPOINT;
		}
		return processors.get(name);
	}

	private String stash(String key) {
		if (functionCatalog.lookupFunction(key) != null) {
			if (!processors.containsKey(key)) {
				processors.put(key, flux -> function(key, flux));
			}
			return key;
		}
		else if (functionCatalog.lookupConsumer(key) != null) {
			if (!processors.containsKey(key)) {
				processors.put(key, flux -> consumer(key, flux));
			}
			return key;
		}
		return null;
	}

	private Function<Message<?>, Object> convertInput(Object function) {
		Class<?> inputType = functionInspector.getInputType(function);
		return m -> {
			if (functionInspector.isMessage(function)) {
				return MessageBuilder.withPayload(convertPayload(inputType, m))
						.copyHeaders(m.getHeaders()).build();
			}
			else {
				return convertPayload(inputType, m);
			}
		};
	}

	private Object convertPayload(Class<?> inputType, Message<?> m) {
		Object result;
		if (inputType.isAssignableFrom(m.getPayload().getClass())) {
			result = m.getPayload();
		}
		else {
			result = this.converter.fromMessage(m, inputType);
		}
		if (result == null) {
			result = UNCONVERTED;
		}
		return result;
	}

	interface FluxMessageProcessor {
		Flux<Message<?>> process(Flux<Message<?>> flux);
	}

}
