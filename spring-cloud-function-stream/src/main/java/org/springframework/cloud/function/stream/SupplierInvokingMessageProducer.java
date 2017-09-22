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

import java.util.function.Supplier;

import org.springframework.cloud.function.core.FunctionCatalog;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * @author Mark Fisher
 */
public class SupplierInvokingMessageProducer<T> extends MessageProducerSupport {

	private final FunctionCatalog functionCatalog;

	public SupplierInvokingMessageProducer(FunctionCatalog registry) {
		this.functionCatalog = registry;
		this.setOutputChannelName(Source.OUTPUT);
	}

	@Override
	protected void doStart() {
		supplier().subscribeOn(Schedulers.elastic()).subscribe(m -> this.sendMessage(m));
	}

	private Flux<Message<?>> supplier() {
		Supplier<Flux<?>> supplier = null;
		Flux<Message<?>> result = Flux.empty();
		for (String name : functionCatalog.getSupplierNames()) {
			supplier = functionCatalog.lookupSupplier(name);
			Assert.notNull(supplier, "Supplier must not be null");
			result = Flux.merge(result,
					supplier.get().map(payload -> MessageBuilder.withPayload(payload)
							.setHeader(StreamConfigurationProperties.ROUTE_KEY, name)
							.build()));
		}
		return result;
	}
}
