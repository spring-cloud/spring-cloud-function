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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * Miscellaneous utility methods to deal with Cloud Events - https://cloudevents.io/.
 * <br>
 * Mainly for internal use within the framework;
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
public final class CloudEventMessageUtils {

	private CloudEventMessageUtils() {

	}

	/**
	 * String value of 'application/cloudevents' mime type.
	 */
	public static String APPLICATION_CLOUDEVENTS_VALUE = "application/cloudevents";

	/**
	 * {@link MimeType} instance representing 'application/cloudevents' mime type.
	 */
	public static MimeType APPLICATION_CLOUDEVENTS = MimeTypeUtils.parseMimeType(APPLICATION_CLOUDEVENTS_VALUE);

	/**
	 * Prefix for attributes.
	 */
	public static String ATTR_PREFIX = "ce-";

	/**
	 * Value for 'data' attribute.
	 */
	public static String DATA = "data";

	/**
	 * Value for 'data' attribute with prefix.
	 */
	public static String CE_DATA = ATTR_PREFIX + DATA;

	/**
	 * Value for 'id' attribute.
	 */
	public static String ID = "id";

	/**
	 * Value for 'id' attribute with prefix.
	 */
	public static String CE_ID = ATTR_PREFIX + ID;

	/**
	 * Value for 'source' attribute.
	 */
	public static String SOURCE = "source";

	/**
	 * Value for 'source' attribute with prefix.
	 */
	public static String CE_SOURCE = ATTR_PREFIX + SOURCE;

	/**
	 * Value for 'specversion' attribute.
	 */
	public static String SPECVERSION = "specversion";

	/**
	 * Value for 'specversion' attribute with prefix.
	 */
	public static String CE_SPECVERSION = ATTR_PREFIX + SPECVERSION;

	/**
	 * Value for 'type' attribute.
	 */
	public static String TYPE = "type";

	/**
	 * Value for 'type' attribute with prefix.
	 */
	public static String CE_TYPE = ATTR_PREFIX + TYPE;

	/**
	 * Value for 'datacontenttype' attribute.
	 */
	public static String DATACONTENTTYPE = "datacontenttype";

	/**
	 * Value for 'datacontenttype' attribute with prefix.
	 */
	public static String CE_DATACONTENTTYPE = ATTR_PREFIX + DATACONTENTTYPE;

	/**
	 * Value for 'dataschema' attribute.
	 */
	public static String DATASCHEMA = "dataschema";

	/**
	 * Value for 'dataschema' attribute with prefix.
	 */
	public static String CE_DATASCHEMA = ATTR_PREFIX + DATASCHEMA;

	/**
	 * Value for 'subject' attribute.
	 */
	public static String SUBJECT = "subject";

	/**
	 * Value for 'subject' attribute with prefix.
	 */
	public static String CE_SUBJECT = ATTR_PREFIX + SUBJECT;

	/**
	 * Value for 'time' attribute.
	 */
	public static String TIME = "time";

	/**
	 * Value for 'time' attribute with prefix.
	 */
	public static String CE_TIME = ATTR_PREFIX + TIME;

	/**
	 * Checks if {@link Message} represents cloud event in binary-mode.
	 */
	public static boolean isBinary(Map<String, Object> headers) {
		return (headers.containsKey(ID)
				&& headers.containsKey(SOURCE)
				&& headers.containsKey(SPECVERSION)
				&& headers.containsKey(TYPE))
				||
				(headers.containsKey(CE_ID)
				&& headers.containsKey(CE_SOURCE)
				&& headers.containsKey(CE_SPECVERSION)
				&& headers.containsKey(CE_TYPE));
	}


	/**
	 * Will construct instance of {@link CloudEventAttributes} setting its required attributes.
	 *
	 * @param ce_id value for Cloud Event 'id' attribute
	 * @param ce_specversion value for Cloud Event 'specversion' attribute
	 * @param ce_source value for Cloud Event 'source' attribute
	 * @param ce_type value for Cloud Event 'type' attribute
	 * @return instance of {@link CloudEventAttributes}
	 */
	public static CloudEventAttributes get(String ce_id, String ce_specversion, String ce_source, String ce_type) {
		Assert.hasText(ce_id, "'ce_id' must not be null or empty");
		Assert.hasText(ce_specversion, "'ce_specversion' must not be null or empty");
		Assert.hasText(ce_source, "'ce_source' must not be null or empty");
		Assert.hasText(ce_type, "'ce_type' must not be null or empty");
		Map<String, Object> requiredAttributes = new HashMap<>();
		requiredAttributes.put(CloudEventMessageUtils.CE_ID, ce_id);
		requiredAttributes.put(CloudEventMessageUtils.CE_SPECVERSION, ce_specversion);
		requiredAttributes.put(CloudEventMessageUtils.CE_SOURCE, ce_source);
		requiredAttributes.put(CloudEventMessageUtils.CE_TYPE, ce_type);
		return new CloudEventAttributes(requiredAttributes);
	}

	/**
	 * Will construct instance of {@link CloudEventAttributes}
	 * Should default/generate cloud event ID and SPECVERSION.
	 *
	 * @param ce_source value for Cloud Event 'source' attribute
	 * @param ce_type value for Cloud Event 'type' attribute
	 * @return instance of {@link CloudEventAttributes}
	 */
	public static CloudEventAttributes get(String ce_source, String ce_type) {
		return get(UUID.randomUUID().toString(), "1.0", ce_source, ce_type);
	}

	/**
	 * Will construct instance of {@link CloudEventAttributes} from {@link MessageHeaders}.
	 *
	 * Should copy Cloud Event related headers into an instance of {@link CloudEventAttributes}
	 * NOTE: Certain headers must not be copied.
	 *
	 * @param headers instance of {@link MessageHeaders}
	 * @return modifiable instance of {@link CloudEventAttributes}
	 */
	public static RequiredAttributeAccessor get(MessageHeaders headers) {
		return new RequiredAttributeAccessor(headers);
	}
}
