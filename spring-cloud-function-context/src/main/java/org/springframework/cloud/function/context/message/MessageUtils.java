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

package org.springframework.cloud.function.context.message;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public abstract class MessageUtils {

	/**
	 * Value for 'message-type' typically use as header key.
	 */
	public static String MESSAGE_TYPE = "message-type";

	/**
	 * Value for 'target-protocol' typically use as header key.
	 */
	public static String TARGET_PROTOCOL = "target-protocol";

	/**
	 * Value for 'target-protocol' typically use as header key.
	 */
	public static String SOURCE_TYPE = "source-type";
}
