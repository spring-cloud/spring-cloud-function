/*
 * Copyright 2016-2017 the original author or authors.
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

package org.springframework.cloud.function.context.catalog;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.util.Assert;

/**
 * @author Dave Syer
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 */
public class InMemoryFunctionCatalog
		implements FunctionRegistry, ApplicationEventPublisherAware {

	private final Map<String, Function<?, ?>> functions;

	private final Map<String, Consumer<?>> consumers;

	private final Map<String, Supplier<?>> suppliers;

	@Autowired(required = false)
	private ApplicationEventPublisher publisher;

	public InMemoryFunctionCatalog() {
		this(Collections.emptySet());
	}

	public InMemoryFunctionCatalog(Set<FunctionRegistration<?>> registrations) {
		Assert.notNull(registrations, "'registrations' must not be null");
		this.suppliers = new HashMap<>();
		this.functions = new HashMap<>();
		this.consumers = new HashMap<>();
		registrations.stream().forEach(reg -> reg.getNames().stream().forEach(name -> {
			if (reg.getTarget() instanceof Consumer) {
				consumers.put(name, (Consumer<?>) reg.getTarget());
			}
			else if (reg.getTarget() instanceof Function) {
				functions.put(name, (Function<?, ?>) reg.getTarget());
			}
			else if (reg.getTarget() instanceof Supplier) {
				suppliers.put(name, (Supplier<?>) reg.getTarget());
			}
		}));
	}

	@Override
	public <T> void register(FunctionRegistration<T> registration) {
		Map<String, ?> values = null;
		FunctionRegistrationEvent event;
		if (registration.getTarget() instanceof Function) {
			values = this.functions;
			event = new FunctionRegistrationEvent(this, Function.class,
					registration.getNames());
		}
		else if (registration.getTarget() instanceof Supplier) {
			values = this.suppliers;
			event = new FunctionRegistrationEvent(this, Supplier.class,
					registration.getNames());
		}
		else {
			values = this.consumers;
			event = new FunctionRegistrationEvent(this, Consumer.class,
					registration.getNames());
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) values;
		for (String name : registration.getNames()) {
			map.put(name, registration.getTarget());
		}
		publisher.publishEvent(event);
	}

	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@PostConstruct
	public void init() {
		if (publisher != null) {
			if (!functions.isEmpty()) {
				publisher.publishEvent(new FunctionRegistrationEvent(this, Function.class,
						functions.keySet()));
			}
			if (!consumers.isEmpty()) {
				publisher.publishEvent(new FunctionRegistrationEvent(this, Consumer.class,
						consumers.keySet()));
			}
			if (!suppliers.isEmpty()) {
				publisher.publishEvent(new FunctionRegistrationEvent(this, Supplier.class,
						suppliers.keySet()));
			}
		}
	}

	@PreDestroy
	public void close() {
		if (publisher != null) {
			if (!functions.isEmpty()) {
				publisher.publishEvent(new FunctionUnregistrationEvent(this,
						Function.class, functions.keySet()));
			}
			if (!consumers.isEmpty()) {
				publisher.publishEvent(new FunctionUnregistrationEvent(this,
						Consumer.class, consumers.keySet()));
			}
			if (!suppliers.isEmpty()) {
				publisher.publishEvent(new FunctionUnregistrationEvent(this,
						Supplier.class, suppliers.keySet()));
			}
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Supplier<T> lookupSupplier(String name) {
		return (Supplier<T>) suppliers.get(name);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T, R> Function<T, R> lookupFunction(String name) {
		return (Function<T, R>) functions.get(name);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Consumer<T> lookupConsumer(String name) {
		return (Consumer<T>) consumers.get(name);
	}

	@Override
	public Set<String> getSupplierNames() {
		return suppliers.keySet();
	}

	@Override
	public Set<String> getFunctionNames() {
		return functions.keySet();
	}

	@Override
	public Set<String> getConsumerNames() {
		return consumers.keySet();
	}
}
