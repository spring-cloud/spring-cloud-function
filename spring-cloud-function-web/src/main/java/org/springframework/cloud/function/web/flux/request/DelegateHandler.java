/*
 * Copyright 2016-2017 the original author or authors.
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

package org.springframework.cloud.function.web.flux.request;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.cloud.function.context.FunctionInspector;

public abstract class DelegateHandler<T> {

	private final ListableBeanFactory factory;
	private FunctionInspector processor;
	private final Object source;

	public DelegateHandler(ListableBeanFactory factory, Object source) {
		this.factory = factory;
		this.source = source;
	}

	public Class<?> type() {
		String name = source instanceof String ? (String) source
				: processor().getName(source);
		return (Class<?>) processor().getInputType(name);
	}

	private FunctionInspector processor() {
		if (processor == null) {
			processor = factory.getBean(FunctionInspector.class);
		}
		return processor;
	}

}