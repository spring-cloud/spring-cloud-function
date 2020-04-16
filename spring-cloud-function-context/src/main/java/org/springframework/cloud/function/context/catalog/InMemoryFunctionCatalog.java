/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.context.catalog;

import java.util.Collections;
import java.util.Set;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.util.Assert;

/**
 * @author Dave Syer
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 *
 * @deprecated since 3.1. End-of-life. Not used by the framework anymore in favor of SimpleFunctionRegistry
 */
@Deprecated
public class InMemoryFunctionCatalog extends AbstractComposableFunctionRegistry {

	public InMemoryFunctionCatalog() {
		this(Collections.emptySet());
	}

	public InMemoryFunctionCatalog(Set<FunctionRegistration<?>> registrations) {
		Assert.notNull(registrations, "'registrations' must not be null");
		registrations.stream().forEach(reg -> register(reg));
	}

	@Override
	protected FunctionType findType(FunctionRegistration<?> functionRegistration, String name) {
		FunctionType functionType = super.findType(functionRegistration, name);
		if (functionType == null) {
			functionType = new FunctionType(functionRegistration.getTarget().getClass());
		}
		return functionType;
	}
}
