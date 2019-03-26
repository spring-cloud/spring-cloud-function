/*
 * Copyright 2016-2017 the original author or authors.
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

	private final Map<Class<?>, Map<String, Object>> functions;

	@Autowired(required = false)
	private ApplicationEventPublisher publisher;

	public InMemoryFunctionCatalog() {
		this(Collections.emptySet());
	}

	public InMemoryFunctionCatalog(Set<FunctionRegistration<?>> registrations) {
		Assert.notNull(registrations, "'registrations' must not be null");
		this.functions = new HashMap<>();
		registrations.stream().forEach(reg -> register(reg));
	}

	@Override
	public <T> void register(FunctionRegistration<T> registration) {
		FunctionRegistrationEvent event;
		Class<?> type;
		if (registration.getTarget() instanceof Function) {
			type = Function.class;
			event = new FunctionRegistrationEvent(this, Function.class,
					registration.getNames());
		}
		else if (registration.getTarget() instanceof Supplier) {
			type = Supplier.class;
			event = new FunctionRegistrationEvent(this, Supplier.class,
					registration.getNames());
		}
		else if (registration.getTarget() instanceof Consumer) {
			type = Consumer.class;
			event = new FunctionRegistrationEvent(this, Consumer.class,
					registration.getNames());
		}
		else {
			type = Object.class;
			event = new FunctionRegistrationEvent(this, Object.class,
					registration.getNames());
		}
		Map<String, Object> map = functions.computeIfAbsent(type, key -> new HashMap<>());
		for (String name : registration.getNames()) {
			map.put(name, registration.getTarget());
		}
		publisher.publishEvent(event);
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@PostConstruct
	public void init() {
		if (publisher != null) {
			if (!functions.isEmpty()) {
				for (Class<?> type : functions.keySet()) {
					publisher.publishEvent(new FunctionRegistrationEvent(this, type,
							functions.get(type).keySet()));
				}
			}
		}
	}

	@PreDestroy
	public void close() {
		if (publisher != null) {
			if (!functions.isEmpty()) {
				for (Class<?> type : functions.keySet()) {
					publisher.publishEvent(new FunctionUnregistrationEvent(this, type,
							functions.get(type).keySet()));
				}
			}
		}
	}

	@Override
	public <T> T lookup(Class<?> type, String name) {
		Map<String, Object> map = null;
		for (Class<?> key : functions.keySet()) {
			if (key != Object.class) {
				if (key.isAssignableFrom(type)) {
					map = functions.get(key);
					break;
				}
			}
		}
		if (map == null) {
			map = functions.get(Object.class);
		}
		@SuppressWarnings("unchecked")
		T result = (T) map.get(name);
		return result;
	}

	@Override
	public Set<String> getNames(Class<?> type) {
		Map<String, Object> map = null;
		for (Class<?> key : functions.keySet()) {
			if (key != Object.class) {
				if (key.isAssignableFrom(type)) {
					map = functions.get(key);
					break;
				}
			}
		}
		if (map == null) {
			map = functions.get(Object.class);
		}
		return map == null ? Collections.emptySet() : map.keySet();
	}

}
