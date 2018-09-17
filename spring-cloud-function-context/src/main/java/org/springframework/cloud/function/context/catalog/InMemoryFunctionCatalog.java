/*
 * Copyright 2016-2018 the original author or authors.
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
		implements FunctionRegistry, FunctionInspector, ApplicationEventPublisherAware {

	private final Map<Class<?>, Map<String, Object>> functions;

	private final Map<Object, FunctionRegistration<?>> registrations;

	private ApplicationEventPublisher publisher;

	public InMemoryFunctionCatalog() {
		this(Collections.emptySet());
	}

	public InMemoryFunctionCatalog(Set<FunctionRegistration<?>> registrations) {
		Assert.notNull(registrations, "'registrations' must not be null");
		this.functions = new HashMap<>();
		this.registrations = new HashMap<>();
		registrations.stream().forEach(reg -> register(reg));
	}

	@Override
	public FunctionRegistration<?> getRegistration(Object function) {
		return this.registrations.get(function);
	}

	@Override
	public <T> void register(FunctionRegistration<T> registration) {
		Assert.notEmpty(registration.getNames(), "'registration' must contain at least one name before it is registered in catalog.");
		Class<?> type = Object.class;
		if (registration.getTarget() instanceof Function) {
			type = Function.class;
		}
		else if (registration.getTarget() instanceof Supplier) {
			type = Supplier.class;
		}
		else if (registration.getTarget() instanceof Consumer) {
			type = Consumer.class;
		}
		FunctionRegistrationEvent event = new FunctionRegistrationEvent(this, type, registration.getNames());

		registrations.put(registration.getTarget(), registration);
		FunctionRegistration<T> wrapped = registration.wrap();
		if (wrapped != registration) {
			registration = wrapped;
			registrations.put(wrapped.getTarget(), wrapped);
			if (type == Consumer.class) {
				type = Function.class;
			}
		}
		Map<String, Object> map = functions.computeIfAbsent(type, key -> new HashMap<>());
		for (String name : registration.getNames()) {
			map.put(name, registration.getTarget());
		}
		this.publishEvent(event);
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@PostConstruct
	public void init() {
		if (publisher != null && !functions.isEmpty()) {
			functions.keySet()
				.forEach(type -> this.publishEvent(new FunctionRegistrationEvent(this, type, functions.get(type).keySet())));
		}
	}

	@PreDestroy
	public void close() {
		if (publisher != null && !functions.isEmpty()) {
			functions.keySet()
				.forEach(type -> this.publishEvent(new FunctionUnregistrationEvent(this, type, functions.get(type).keySet())));
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T lookup(Class<?> type, String name) {
		T function = null;
		if (type == null) {
			function = (T) functions.values().stream().filter(map -> map.get(name) != null).map(map -> map.get(name)).findFirst().orElse(null);
		}
		else {
			function = (T) this.extractTypeMap(type).get(name);
		}
		return function;
	}

	@Override
	public Set<String> getNames(Class<?> type) {
		Map<String, Object> map = this.extractTypeMap(type);
		return map == null ? Collections.emptySet() : map.keySet();
	}

	private Map<String, Object> extractTypeMap(Class<?> type) {
		return functions.keySet().stream()
				.filter(key -> key != Object.class && key.isAssignableFrom(type))
				.map(key -> functions.get(key))
				.findFirst().orElse(functions.get(Object.class));
	}

	private void publishEvent(Object event) {
		if (this.publisher != null) {
			this.publisher.publishEvent(event);
		}
	}
}
