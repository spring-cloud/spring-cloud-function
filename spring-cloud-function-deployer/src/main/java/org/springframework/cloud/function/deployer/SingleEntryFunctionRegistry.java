/*
 * Copyright 2016-2018 the original author or authors.
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
package org.springframework.cloud.function.deployer;

import java.util.Collections;
import java.util.Set;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;

/**
 * @author Dave Syer
 *
 */
public class SingleEntryFunctionRegistry implements FunctionRegistry {

	private final FunctionRegistry delegate;

	private final String name;

	public SingleEntryFunctionRegistry(FunctionRegistry delegate, String name) {
		this.delegate = delegate;
		this.name = name;
	}

	@Override
	public <T> T lookup(Class<?> type, String name) {
		return this.name.equals(name) ? this.delegate.lookup(type, name) : null;
	}

	@Override
	public Set<String> getNames(Class<?> type) {
		Set<String> names = this.delegate.getNames(type);
		return names.contains(this.name) ? Collections.singleton(this.name)
				: Collections.emptySet();
	}

	@Override
	public <T> void register(FunctionRegistration<T> registration) {
		this.delegate.register(registration);
	}

}
