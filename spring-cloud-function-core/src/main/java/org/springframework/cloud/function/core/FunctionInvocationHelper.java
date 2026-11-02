/*
 * Copyright 2020-present the original author or authors.
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

import org.springframework.lang.Nullable;

/**
 * Strategy hook allowing extensions (e.g., Cloud Events support) to customize how a
 * function's input and output are processed around an invocation. All methods have
 * pass-through defaults, so implementations override only what they need.
 *
 * @param <I> the type of the function input handled by this helper (typically
 * {@code Message<?>})
 * @author Oleg Zhurakousky
 * @author John Blum
 * @since 3.1
 */
public interface FunctionInvocationHelper<I> {

	/**
	 * Determine whether the function output should be retained as a message (as opposed
	 * to being unwrapped to its payload) for the given input.
	 * @param input the function input
	 * @return {@code true} (the default) if the output should be retained as a message
	 */
	default boolean isRetainOutputAsMessage(I input) {
		return true;
	}

	/**
	 * Called before the input is passed to the function.
	 * @param input the function input
	 * @param inputConverter the message converter used for input conversion; typed as
	 * {@code Object} since this module does not depend on {@code spring-messaging}
	 * (at runtime it is a {@code MessageConverter})
	 * @return the (possibly modified) input; the default returns it unchanged
	 */
	default I preProcessInput(I input, Object inputConverter) {
		return input;
	}

	/**
	 * Called after the function has produced a result.
	 * @param result the function result, may be {@code null}
	 * @param input the input that produced the result
	 * @return the (possibly modified) result; the default returns it unchanged
	 */
	@Nullable
	default Object postProcessResult(@Nullable Object result, I input) {
		return result;
	}
}
