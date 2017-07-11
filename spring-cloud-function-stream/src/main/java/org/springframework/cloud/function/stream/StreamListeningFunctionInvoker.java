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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cloud.function.context.FunctionInspector;
import org.springframework.cloud.function.registry.FunctionCatalog;
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

	private final String defaultEndpoint;

	private final Map<String, FluxMessageProcessor> processors = new HashMap<>();

	private int count = -1;

	private static final FluxMessageProcessor NOENDPOINT = flux -> Flux.empty();

	public StreamListeningFunctionInvoker(FunctionCatalog functionCatalog,
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
	public Mono<Void> handle(@Input(Processor.INPUT) Flux<Message<?>> input,
			@Output(Processor.OUTPUT) FluxSender output) {
		return output.send(
				input.groupBy(this::select).flatMap(group -> group.key().process(group)));
	}

	private Flux<Message<?>> function(String name, Flux<Message<?>> flux) {
		// TODO: the routing key could be added here, but really it should be added in
		// Spring Cloud Stream
		// (https://github.com/spring-cloud/spring-cloud-stream/issues/1010)
		AtomicReference<Map<String, Object>> headers = new AtomicReference<Map<String, Object>>(
				new LinkedHashMap<>());
		return ((Flux<?>) functionCatalog.lookupFunction(name).apply(flux.map(message -> {
			Object applied = convertInput(name).apply(message);
			headers.set(message.getHeaders());
			return applied;
		}))).map(result -> message(result, headers.get()));
	}

	private Message<?> message(Object result, Map<String, Object> headers) {
		return result instanceof Message ? (Message<?>) result
				: MessageBuilder.withPayload(result).copyHeadersIfAbsent(headers).build();
	}

	private Flux<Message<?>> consumer(String name, Flux<Message<?>> flux) {
		functionCatalog.lookupConsumer(name)
				.accept(flux.map(message -> convertInput(name).apply(message)));
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
		String name = defaultEndpoint;
		if (name != null) {
			name = stash(name);
		}
		if (name == null) {
			if (input.getHeaders().containsKey(StreamConfigurationProperties.ROUTE_KEY)) {
				String key = (String) input.getHeaders()
						.get(StreamConfigurationProperties.ROUTE_KEY);
				name = stash(key);
			}
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
					Class<?> inputType = functionInspector.getInputType(candidate);
					Object value = this.converter.fromMessage(input, inputType);
					if (value != null && inputType.isInstance(value)) {
						matches.add(candidate);
					}
				}
				if (matches.size() == 1) {
					name = stash(matches.iterator().next());
				}
				else {
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

	interface FluxMessageProcessor {
		Flux<Message<?>> process(Flux<Message<?>> flux);
	}

}
