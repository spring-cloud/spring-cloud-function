/*
 * Copyright 2015-present the original author or authors.
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

package org.springframework.cloud.function.context.config;

import org.springframework.messaging.Message;

/**
 * @author Oleg Zhurakousky
 */
public interface MessageConverterHelper {

	/**
	 * This method will be called by the framework in cases when a message failed to convert.
	 * It allows you to signal to the framework if such failure should be considered fatal or not.
	 *
	 * @param message failed message
	 * @return true if conversion failure must be considered fatal.
	 */
	default boolean shouldFailIfCantConvert(Message<?> message) {
		return false;
	}


	/**
	 * This method will be called by the framework in cases when a message failed to convert.
	 * It allows you to signal to the framework if such failure should be considered fatal or not.
	 *
	 * @param message failed message
	 * @param t exception (coudl be null)
	 * @return true if conversion failure must be considered fatal.
	 */
	default boolean shouldFailIfCantConvert(Message<?> message, Throwable t) {
		if (t == null) {
			return this.shouldFailIfCantConvert(message);
		}
		return false;
	}

	/**
	 * This method will be called by the framework in cases when a single message within batch of messages failed to convert.
	 * It provides a place for providing post-processing logic before message converter returns.
	 *
	 * @param message failed message.
	 * @param index index of failed message within the batch
	 */
	default void postProcessBatchMessageOnFailure(Message<?> message, int index) {
	}
}
