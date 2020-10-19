/*
 * Copyright 2019-2019 the original author or authors.
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

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;

/**
 * Base class for all wrappers that represent underlying functions (user defined
 * suppliers, functions and/or consumers) as reactive functions.
 *
 * @param <I> input type of target consumer
 * @param <O> output type of target consumer
 * @param <IP> reactive input type of target function (instance of {@link Publisher}
 * @param <OP> reactive output type of target function (instance of {@link Publisher}
 * @param <T> actual target function (instance of {@link Supplier}, {@link Function} or
 * {@link Consumer})
 * @author Oleg Zhurakousky
 * @since 2.0.1
 *
 * @deprecated since 3.1 no longer used by the framework
 */
@Deprecated
public abstract class WrappedFunction<I, O, IP extends Publisher<I>, OP extends Publisher<O>, T>
		implements Function<IP, OP>, FluxWrapper<T> {

	private final T target;

	WrappedFunction(T target) {
		this.target = target;
	}

	@Override
	public T getTarget() {
		return this.target;
	}

}
