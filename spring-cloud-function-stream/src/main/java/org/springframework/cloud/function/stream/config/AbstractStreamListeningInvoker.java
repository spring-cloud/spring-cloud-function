/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.context.message.MessageUtils;
import org.springframework.cloud.stream.converter.CompositeMessageConverterFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Dave Syer
 *
 */
public abstract class AbstractStreamListeningInvoker
		implements SmartInitializingSingleton {

	private final FunctionInspector functionInspector;

	private final FunctionCatalog functionCatalog;

	private final CompositeMessageConverterFactory converterFactory;

	private MessageConverter converter;

	private static final Object UNCONVERTED = new Object();

	private final String defaultRoute;

	private final Map<String, FluxMessageProcessor> processors = new HashMap<>();

	private static final FluxMessageProcessor NOENDPOINT = flux -> Flux.empty();

	private boolean share;

	public AbstractStreamListeningInvoker(FunctionCatalog functionCatalog,
			FunctionInspector functionInspector,
			CompositeMessageConverterFactory converterFactory, String defaultRoute,
			boolean share) {
		this.functionCatalog = functionCatalog;
		this.functionInspector = functionInspector;
		this.converterFactory = converterFactory;
		this.defaultRoute = defaultRoute;
		this.share = share;
	}

	@Override
	public void afterSingletonsInstantiated() {
		this.converter = this.converterFactory.getMessageConverterForAllRegistered();
	}

	protected Mono<Void> consumer(String name, Flux<Message<?>> flux) {
		Consumer<Object> consumer = functionCatalog.lookup(Consumer.class, name);
		flux = flux.publish().refCount(2);
		// The consumer will subscribe to the input flux, so we need to listen separately
		consumer.accept(flux.map(message -> convertInput(consumer).apply(message))
				.filter(transformed -> transformed != UNCONVERTED));
		return flux.then(Mono.empty());
	}

	protected Flux<Message<?>> function(String name, Flux<Message<?>> flux) {
		Function<Object, Flux<?>> function = functionCatalog.lookup(Function.class, name);
		return flux.publish(values -> {
			Flux<?> result = function
					.apply(values.map(message -> convertInput(function).apply(message)));
			if (this.functionInspector.isMessage(function)) {
				result = result.map(message -> MessageUtils.unpack(function, message));
			}
			Flux<Map<String, Object>> aggregate = headers(values);
			return aggregate.withLatestFrom(result,
					(map, payload) -> message(map, payload));
		});
	}

	private Flux<Map<String, Object>> headers(Flux<Message<?>> flux) {
		return flux.map(message -> message.getHeaders());
	}

	private Message<?> message(Map<String, Object> headers, Object result) {
		return result instanceof Message
				? MessageBuilder.fromMessage((Message<?>) result)
						.copyHeadersIfAbsent(headers).build()
				: MessageBuilder.withPayload(result).copyHeadersIfAbsent(headers).build();
	}

	private Function<Message<?>, Object> convertInput(Object function) {
		Class<?> inputType = functionInspector.getInputType(function);
		return m -> {
			if (functionInspector.isMessage(function)) {
				return MessageUtils.create(function, convertPayload(inputType, m),
						m.getHeaders());
			}
			else {
				return convertPayload(inputType, m);
			}
		};
	}

	protected Object convertPayload(Class<?> inputType, Message<?> m) {
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

	private Flux<Message<?>> balance(List<String> names, Flux<Message<?>> flux) {
		if (names.isEmpty()) {
			return Flux.empty();
		}
		flux = flux.hide();
		Flux<Message<?>> result = Flux.empty();
		if (names.size() > 1) {
			if (this.share) {
				flux = flux.publish().refCount(names.size());
			}
			else {
				return Flux.error(new IllegalStateException(
						"Multiple matches and share disabled: " + names));
			}
		}
		for (String name : names) {
			if (functionCatalog.lookup(Consumer.class, name) != null) {
				result = result.mergeWith(
						consumer(name, flux).thenMany(Flux.<Message<?>>empty()));
			}
			else {
				result = result.mergeWith(function(name, flux));
			}
		}
		return result;
	}

	protected FluxMessageProcessor select(Message<?> input) {
		FluxMessageProcessor processor = null;
		if (input.getHeaders().containsKey(StreamConfigurationProperties.ROUTE_KEY)) {
			String key = (String) input.getHeaders()
					.get(StreamConfigurationProperties.ROUTE_KEY);
			processor = stash(key);
		}
		if (processor == null && defaultRoute != null) {
			processor = stash(defaultRoute);
		}
		if (processor == null) {
			Set<String> names = new LinkedHashSet<>(
					functionCatalog.getNames(Function.class));
			names.addAll(functionCatalog.getNames(Consumer.class));
			List<String> matches = new ArrayList<>();
			if (names.size() == 1) {
				String key = names.iterator().next();
				processor = stash(key);
			}
			else {
				for (String candidate : names) {
					Object function = functionCatalog.lookup(Function.class, candidate);
					if (function == null) {
						function = functionCatalog.lookup(Consumer.class, candidate);
					}
					if (function == null) {
						continue;
					}
					Class<?> inputType = functionInspector.getInputType(function);
					Object value = convertPayload(inputType, input);
					if (value != null && inputType.isInstance(value)) {
						matches.add(candidate);
					}
				}
				if (matches.size() == 1) {
					processor = stash(matches.iterator().next());
				}
				else {
					return flux -> balance(matches, flux);
				}
			}
		}
		if (processor == null) {
			return NOENDPOINT;
		}
		return processor;
	}

	private FluxMessageProcessor stash(String key) {
		if (functionCatalog.lookup(Function.class, key) != null) {
			if (!processors.containsKey(key)) {
				processors.put(key, flux -> function(key, flux));
			}
			return processors.get(key);
		}
		else if (functionCatalog.lookup(Consumer.class, key) != null) {
			if (!processors.containsKey(key)) {
				processors.put(key,
						flux -> consumer(key, flux).thenMany(Flux.<Message<?>>empty()));
			}
			return processors.get(key);
		}
		return null;
	}

	interface FluxMessageProcessor {
		Flux<Message<?>> process(Flux<Message<?>> flux);
	}
}