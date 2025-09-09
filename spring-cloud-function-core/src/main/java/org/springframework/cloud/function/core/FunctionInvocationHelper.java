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


/**
 *
 * @author Oleg Zhurakousky
 * @author John Blum
 * @since 3.1
 *
 */
public interface FunctionInvocationHelper<I> {

	default boolean isRetainOutputAsMessage(I input) {
		return true;
	}

	default I preProcessInput(I input, Object inputConverter) {
		return input;
	}

	default Object postProcessResult(Object result, I input) {
		return result;
	}
}
