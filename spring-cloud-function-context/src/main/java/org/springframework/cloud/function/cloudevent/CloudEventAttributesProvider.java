/*
 * Copyright 2020-2020 the original author or authors.
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

package org.springframework.cloud.function.cloudevent;

import org.springframework.messaging.Message;

/**
 * @author Oleg Zhurakousky
 * @since 3.1
 */
@FunctionalInterface
public interface CloudEventAttributesProvider {

	/**
	 * @param inputMessage input message used to invoke user functionality (e.g.,
	 * function)
	 * @param result result of the invocation of user functionality (e.g., function)
	 * @return instance of {@link CloudEventAttributes}
	 */
	default CloudEventAttributes generate(Message<?> inputMessage, Object result) {
		CloudEventAttributes attributes = new CloudEventAttributes(inputMessage.getHeaders());
		return update(attributes.setType(result.getClass().getName()));
	}

	/**
	 * Modify the default attributes as necessary.
	 */
	CloudEventAttributes update(CloudEventAttributes attrs);

}
