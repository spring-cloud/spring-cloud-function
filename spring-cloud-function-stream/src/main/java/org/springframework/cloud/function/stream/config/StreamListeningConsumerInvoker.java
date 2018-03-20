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
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.context.message.MessageUtils;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.converter.CompositeMessageConverterFactory;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConverter;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Dave Syer
 */
public class StreamListeningConsumerInvoker implements SmartInitializingSingleton {

	private final FunctionInspector functionInspector;

	private final FunctionCatalog functionCatalog;

	private final CompositeMessageConverterFactory converterFactory;

	private MessageConverter converter;

	private final String defaultRoute;

	private final Map<String, FluxMessageProcessor> processors = new HashMap<>();

	private static final FluxMessageProcessor NOENDPOINT = flux -> Mono.empty();

	private static final Object UNCONVERTED = new Object();

	private boolean share;

	public StreamListeningConsumerInvoker(FunctionCatalog functionCatalog,
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

	@StreamListener
	public void handle(@Input(Processor.INPUT) Flux<Message<?>> input) {
		input.groupBy(this::select).flatMap(group -> group.key().process(group))
				.subscribe();
	}

	private Mono<Void> consumer(String name, Flux<Message<?>> flux) {
		Consumer<Object> consumer = functionCatalog.lookup(Consumer.class, name);
		consumer.accept(flux.map(message -> convertInput(consumer).apply(message))
				.filter(transformed -> transformed != UNCONVERTED));
		return Mono.empty();
	}

	private Mono<Void> balance(List<String> names, Flux<Message<?>> flux) {
		if (names.isEmpty()) {
			return Mono.empty();
		}
		Flux<?> result = Flux.empty();
		if (names.size() > 1) {
			if (this.share) {
				flux = flux.share();
			}
			else {
				return Mono.error(new IllegalStateException(
						"Multiple matches and share disabled: " + names));
			}
		}
		for (String name : names) {
			result = result.zipWith(consumer(name, flux));
		}
		return result.then();
	}

	private FluxMessageProcessor select(Message<?> input) {
		String name = null;
		if (input.getHeaders().containsKey(StreamConfigurationProperties.ROUTE_KEY)) {
			String key = (String) input.getHeaders()
					.get(StreamConfigurationProperties.ROUTE_KEY);
			name = stash(key);
		}
		if (name == null && defaultRoute != null) {
			name = stash(defaultRoute);
		}
		if (name == null) {
			Set<String> names = new LinkedHashSet<>(
					functionCatalog.getNames(Consumer.class));
			List<String> matches = new ArrayList<>();
			if (names.size() == 1) {
				String key = names.iterator().next();
				name = stash(key);
			}
			else {
				for (String candidate : names) {
					Object function = functionCatalog.lookup(Consumer.class, candidate);
					if (function == null) {
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
		if (functionCatalog.lookup(Consumer.class, key) != null) {
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
				return MessageUtils.create(function, convertPayload(inputType, m),
						m.getHeaders());
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
		Mono<Void> process(Flux<Message<?>> flux);
	}

}
