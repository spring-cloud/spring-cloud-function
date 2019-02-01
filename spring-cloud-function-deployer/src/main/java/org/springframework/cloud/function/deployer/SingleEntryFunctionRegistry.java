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

package org.springframework.cloud.function.deployer;

import java.util.Collections;
import java.util.Set;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.util.StringUtils;

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
		if (StringUtils.isEmpty(name)) {
			if (this.delegate.getNames(type).size() == 1) {
				return this.delegate.lookup(type,
						this.delegate.getNames(type).iterator().next());
			}
			name = this.name;
		}
		return name.equals(this.name) ? this.delegate.lookup(type, name) : null;
	}

	@Override
	public Set<String> getNames(Class<?> type) {
		return Collections.singleton(this.name);
	}

	@Override
	public <T> void register(FunctionRegistration<T> registration) {
		this.delegate.register(registration);
	}

}
