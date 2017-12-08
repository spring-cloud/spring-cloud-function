/*
 * Copyright 2016-2017 the original author or authors.
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

import java.util.HashSet;
import java.util.Set;

/**
 * @author Dave Syer
 *
 */
@SuppressWarnings("serial")
public class FunctionUnregistrationEvent extends FunctionCatalogEvent {

	private final Class<?> type;
	private final Set<String> names;

	public FunctionUnregistrationEvent(Object source, Class<?> type, Set<String> names) {
		super(source);
		this.type = type;
		this.names = new HashSet<>(names);
	}

	public Class<?> getType() {
		return type;
	}

	public Set<String> getNames() {
		return names;
	}

}
