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

import java.util.Collections;
import java.util.Map;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
public interface CloudEventAtttributesProvider {

	/**
	 * Will construct instance of {@link CloudEventAttributes} setting its required attributes.
	 *
	 * @param ce_id value for Cloud Event 'id' attribute
	 * @param ce_specversion value for Cloud Event 'specversion' attribute
	 * @param ce_source value for Cloud Event 'source' attribute
	 * @param ce_type value for Cloud Event 'type' attribute
	 * @return instance of {@link CloudEventAttributes}
	 */
	CloudEventAttributes get(String ce_id, String ce_specversion, String ce_source, String ce_type);

	/**
	 * Will construct instance of {@link CloudEventAttributes}
	 * Should default/generate cloud event ID and SPECVERSION.
	 *
	 * @param ce_source value for Cloud Event 'source' attribute
	 * @param ce_type value for Cloud Event 'type' attribute
	 * @return instance of {@link CloudEventAttributes}
	 */
	CloudEventAttributes get(String ce_source, String ce_type);


	/**
	 * Will construct instance of {@link CloudEventAttributes} from {@link MessageHeaders}.
	 *
	 * Should copy Cloud Event related headers into an instance of {@link CloudEventAttributes}
	 * NOTE: Certain headers must not be copied.
	 *
	 * @param headers instance of {@link MessageHeaders}
	 * @return modifiable instance of {@link CloudEventAttributes}
	 */
	RequiredAttributeAccessor get(MessageHeaders headers);

	/**
	 *
	 * @param inputMessage input message used to invoke user functionality (e.g., function)
	 * @param result result of the invocation of user functionality (e.g., function)
	 * @return instance of {@link CloudEventAttributes}
	 */
	Map<String, Object> generateDefaultCloudEventHeaders(Message<?> inputMessage, Object result);
}
