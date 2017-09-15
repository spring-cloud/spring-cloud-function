/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.function.compiler.proxy;

import java.util.function.Supplier;

import org.springframework.cloud.function.core.FunctionFactoryMetadata;
import org.springframework.core.io.Resource;

/**
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 *
 * @param <T> type
 */
public class ByteCodeLoadingSupplier<T> extends AbstractByteCodeLoadingProxy<Supplier<T>> implements FunctionFactoryMetadata<Supplier<T>>, Supplier<T> {

	public ByteCodeLoadingSupplier(Resource resource) {
		super(resource);
	}

	@Override
	public T get() {
		return this.getTarget().get();
	}
}
