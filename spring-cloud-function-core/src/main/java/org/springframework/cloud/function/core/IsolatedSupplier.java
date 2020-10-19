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

package org.springframework.cloud.function.core;

import java.util.function.Supplier;

import org.springframework.util.ClassUtils;

/**
 * @param <T> supplied type
 * @author Dave Syer
 * @deprecated since 3.1 no longer used by the framework
 */
@Deprecated
public class IsolatedSupplier<T> implements Supplier<T>, Isolated {

	private final Supplier<T> supplier;

	private final ClassLoader classLoader;

	public IsolatedSupplier(Supplier<T> supplier) {
		this.supplier = supplier;
		this.classLoader = supplier.getClass().getClassLoader();
	}

	@Override
	public ClassLoader getClassLoader() {
		return this.classLoader;
	}

	@Override
	public T get() {
		ClassLoader context = ClassUtils
				.overrideThreadContextClassLoader(this.classLoader);
		try {
			return this.supplier.get();
		}
		finally {
			ClassUtils.overrideThreadContextClassLoader(context);
		}
	}

}
