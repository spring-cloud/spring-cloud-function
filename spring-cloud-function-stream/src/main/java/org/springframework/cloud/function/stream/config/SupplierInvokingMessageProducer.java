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

package org.springframework.cloud.function.stream.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.message.MessageUtils;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * @author Mark Fisher
 */
public class SupplierInvokingMessageProducer<T> extends MessageProducerSupport {

	private final FunctionCatalog functionCatalog;

	private final Set<String> suppliers = new HashSet<>();

	private final Map<String, Disposable> disposables = new HashMap<>();

	public SupplierInvokingMessageProducer(FunctionCatalog registry) {
		this.functionCatalog = registry;
		this.setOutputChannelName(Source.OUTPUT);
	}

	@Override
	protected void doStart() {
		for (String name : functionCatalog.getSupplierNames()) {
			start(name);
		}
	}

	@Override
	protected void doStop() {
		for (String name : new HashSet<>(suppliers)) {
			stop(name);
		}
	}

	public void stop(String name) {
		if (disposables.containsKey(name)) {
			synchronized (disposables) {
				if (disposables.containsKey(name)) {
					try {
						disposables.get(name).dispose();
					}
					finally {
						disposables.remove(name);
						suppliers.remove(name);
					}
				}
			}
		}
	}

	public void start(String name) {
		if (!disposables.containsKey(name)) {
			synchronized (disposables) {
				if (!disposables.containsKey(name)) {
					Supplier<Flux<?>> supplier = functionCatalog.lookupSupplier(name);
					if (supplier != null) {
						suppliers.add(name);
						disposables.put(name,
								supplier.get().subscribeOn(Schedulers.elastic())
										.subscribe(m -> send(name, m)));
					}
				}
			}
		}
	}

	private void send(String name, Object payload) {
		Supplier<Flux<?>> supplier = functionCatalog.lookupSupplier(name);
		Message<?> message = MessageUtils.unpack(supplier, payload);
		message = MessageBuilder.fromMessage(message)
				.setHeaderIfAbsent(StreamConfigurationProperties.ROUTE_KEY, name).build();
		getOutputChannel().send(message);
	}

}
