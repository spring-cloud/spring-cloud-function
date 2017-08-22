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

import java.util.function.Consumer;

import org.springframework.cloud.function.support.FunctionFactoryMetadata;
import org.springframework.core.io.Resource;

/**
 * @author Mark Fisher
 *
 * @param <T> type
 */
public class ByteCodeLoadingConsumer<T> extends AbstractByteCodeLoadingProxy<Consumer<T>> implements FunctionFactoryMetadata<Consumer<T>>, Consumer<T> {

	public ByteCodeLoadingConsumer(Resource resource) {
		super(resource, Consumer.class);
	}

	@Override
	public void accept(T t) {
		this.getTarget().accept(t);
	}
}
