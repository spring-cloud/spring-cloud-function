/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.cloud.function.context;

import java.util.function.Function;

import org.springframework.messaging.Message;

/**
 * Strategy for implementing function with post processing behavior. <br>
 * The core framework only provides support for the post-processing behavior. The actual
 * invocation of post-processing is left to the end user or the framework which integrates
 * Spring Cloud Function. This is because post-processing can mean different things in
 * different execution contexts. See {@link #postProcess(Message)} method for more
 * information.
 *
 * @param <I> - input type
 * @param <O> - output type
 * @author Oleg Zhurakousky
 * @since 4.0.3
 *
 */
public interface PostProcessingFunction<I, O> extends Function<I, O> {

	@SuppressWarnings("unchecked")
	@Override
	default O apply(I t) {
		return (O) t;
	}

	/**
	 * Will post process the result of this's function invocation after this function has
	 * been triggered. <br>
	 * This operation is not managed/invoked by the core functionality of the Spring Cloud
	 * Function. It is specifically designed as a hook for other frameworks and extensions
	 * to invoke after this function was "triggered" and there is a requirement to do some
	 * post processing. The word "triggered" can mean different things in different
	 * execution contexts. For example, in spring-cloud-stream it means that the function
	 * has been invoked and the result of the function has been sent to the target
	 * destination.
	 *
	 * The boolean value argument - 'success' - allows the triggering framework to signal
	 * success or failure of its triggering operation whatever that may mean.
	 * @param result - the result of function invocation as an instance of {@link Message}
	 * including all the metadata as message headers.
	 */
	default void postProcess(Message<O> result) {
	}

}
