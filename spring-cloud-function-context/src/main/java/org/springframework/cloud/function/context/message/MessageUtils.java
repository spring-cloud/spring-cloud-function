/*
 * Copyright 2012-present the original author or authors.
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

import java.util.Map;
import java.util.TreeMap;

import org.springframework.messaging.Message;

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
	public static String SOURCE_TYPE = "source-type";

	/**
	 * Returns (payload, headers) structure identical to `message` while substituting
	 * headers with case insensitive map.
	 */
	public static MessageStructureWithCaseInsensitiveHeaderKeys toCaseInsensitiveHeadersStructure(Message<?> message) {
		return new MessageStructureWithCaseInsensitiveHeaderKeys(message);
	}

	/**
	 * !!! INTERNAL USE ONLY, MAY CHANGE OR REMOVED WITHOUT NOTICE!!!
	 */
	@SuppressWarnings({ "rawtypes" })
	public static class MessageStructureWithCaseInsensitiveHeaderKeys {

		private final Object payload;

		private final Map headers;

		@SuppressWarnings("unchecked")
		MessageStructureWithCaseInsensitiveHeaderKeys(Message message) {
			this.payload = message.getPayload();
			this.headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			this.headers.putAll(message.getHeaders());
		}

		public Object getPayload() {
			return payload;
		}

		public Map getHeaders() {
			return headers;
		}

	}

}
