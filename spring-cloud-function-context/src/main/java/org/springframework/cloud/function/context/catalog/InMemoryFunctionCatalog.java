/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.cloud.function.context.catalog;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.util.Assert;

/**
 * @author Dave Syer
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 */
public class InMemoryFunctionCatalog extends AbstractComposableFunctionRegistry {

	private final Map<Object, FunctionRegistration<?>> registrations;

	public InMemoryFunctionCatalog() {
		this(Collections.emptySet());
	}

	public InMemoryFunctionCatalog(Set<FunctionRegistration<?>> registrations) {
		Assert.notNull(registrations, "'registrations' must not be null");
		this.registrations = new HashMap<>();
		registrations.stream().forEach(reg -> register(reg));
	}

	@Override
	public FunctionRegistration<?> getRegistration(Object function) {
		return this.registrations.get(function);
	}

	@Override
	public <T> void register(FunctionRegistration<T> registration) {
		Assert.notEmpty(registration.getNames(),
				"'registration' must contain at least one name before it is registered in catalog.");
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
		FunctionRegistrationEvent event = new FunctionRegistrationEvent(this, type,
				registration.getNames());

		this.registrations.put(registration.getTarget(), registration);
		FunctionRegistration<T> wrapped = registration.wrap();
		if (wrapped != registration) {
			registration = wrapped;
			this.registrations.put(wrapped.getTarget(), wrapped);
			if (type == Consumer.class) {
				type = Function.class;
			}
		}

		for (String name : registration.getNames()) {
			this.addFunction(name, registration.getTarget());
		}
		this.publishEvent(event);
	}

	private void publishEvent(Object event) {
		if (this.applicationEventPublisher != null) {
			this.applicationEventPublisher.publishEvent(event);
		}
	}

}
