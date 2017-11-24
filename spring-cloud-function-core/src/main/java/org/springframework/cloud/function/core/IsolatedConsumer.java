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

package org.springframework.cloud.function.core;

import java.util.function.Consumer;

import org.springframework.util.ClassUtils;

/**
 * @author Dave Syer
 *
 */
public class IsolatedConsumer<T> implements Consumer<T> {

	private final Consumer<T> consumer;

	public IsolatedConsumer(Consumer<T> consumer) {
		this.consumer = consumer;
	}

	@Override
	public void accept(T item) {
		ClassLoader context = ClassUtils
				.overrideThreadContextClassLoader(consumer.getClass().getClassLoader());
		try {
			consumer.accept(item);
		}
		finally {
			ClassUtils.overrideThreadContextClassLoader(context);
		}
	}

}
