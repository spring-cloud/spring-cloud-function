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

import java.lang.reflect.Field;
import java.net.URI;
import java.time.OffsetTime;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.ContentTypeResolver;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Miscellaneous utility methods to assist with representing Cloud Event as Spring
 * {@link Message}. <br>
 * Primarily intended for the internal use within Spring-based frameworks and
 * integrations.
 *
 * @author Oleg Zhurakousky
 * @author Dave Syer
 * @since 3.1
 */
public final class CloudEventMessageUtils {

	private static final ContentTypeResolver contentTypeResolver = new DefaultContentTypeResolver();

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
	public static String DEFAULT_ATTR_PREFIX = "ce_";

	/**
	 * AMQP attributes prefix.
	 */
	public static String AMQP_ATTR_PREFIX = "cloudEvents:";

	/**
	 * Prefix for attributes.
	 */
	public static String HTTP_ATTR_PREFIX = "ce-";

	/**
	 * Value for 'data' attribute.
	 */
	public static String DATA = "data";

	/**
	 * Value for 'id' attribute.
	 */
	public static String ID = "id";

	/**
	 * Value for 'source' attribute.
	 */
	public static String SOURCE = "source";

	/**
	 * Value for 'specversion' attribute.
	 */
	public static String SPECVERSION = "specversion";

	/**
	 * Value for 'type' attribute.
	 */
	public static String TYPE = "type";

	/**
	 * Value for 'datacontenttype' attribute.
	 */
	public static String DATACONTENTTYPE = "datacontenttype";

	/**
	 * Value for 'dataschema' attribute.
	 */
	public static String DATASCHEMA = "dataschema";

	/**
	 * V03 name for 'dataschema' attribute.
	 */
	public static final String SCHEMAURL = "schemaurl";

	/**
	 * Value for 'subject' attribute.
	 */
	public static String SUBJECT = "subject";

	/**
	 * Value for 'time' attribute.
	 */
	public static String TIME = "time";

	public static String getId(Message<?> message) {
		if (message.getHeaders().containsKey("_id")) {
			return (String) message.getHeaders().get("_id");
		}
		String prefix = determinePrefixToUse(message.getHeaders());
		return (String) message.getHeaders().get(prefix + MessageHeaders.ID);
	}

	public static URI getSource(Message<?> message) {
		String prefix = determinePrefixToUse(message.getHeaders());
		return safeGetURI(message.getHeaders(), prefix + SOURCE);
	}

	public static String getSpecVersion(Message<?> message) {
		String prefix = determinePrefixToUse(message.getHeaders());
		return (String) message.getHeaders().get(prefix + SPECVERSION);
	}

	public static String getType(Message<?> message) {
		String prefix = determinePrefixToUse(message.getHeaders());
		return (String) message.getHeaders().get(prefix + TYPE);
	}

	public static String getDataContentType(Message<?> message) {
		String prefix = determinePrefixToUse(message.getHeaders());
		return (String) message.getHeaders().get(prefix + DATACONTENTTYPE);
	}

	public static URI getDataSchema(Message<?> message) {
		String prefix = determinePrefixToUse(message.getHeaders());
		return safeGetURI(message.getHeaders(), prefix + DATASCHEMA);
	}

	public static String getSubject(Message<?> message) {
		String prefix = determinePrefixToUse(message.getHeaders());
		return (String) message.getHeaders().get(prefix + SUBJECT);
	}

	public static OffsetTime getTime(Message<?> message) {
		String prefix = determinePrefixToUse(message.getHeaders());
		return (OffsetTime) message.getHeaders().get(prefix + TIME);
	}

	@SuppressWarnings("unchecked")
	protected static Message<?> toCannonical(Message<?> inputMessage, MessageConverter messageConverter) {

		Field headersField = ReflectionUtils.findField(MessageHeaders.class, "headers");
		headersField.setAccessible(true);
		Map<String, Object> headers = (Map<String, Object>) ReflectionUtils.getField(headersField, inputMessage.getHeaders());
		canonicalizeHeaders(headers);

		String inputContentType = (String) inputMessage.getHeaders().get(DATACONTENTTYPE);
		// first check the obvious and see if content-type is `cloudevents`
		if (!isBinary(inputMessage) && headers.containsKey(MessageHeaders.CONTENT_TYPE)) {
			MimeType contentType = contentTypeResolver.resolve(inputMessage.getHeaders());
			if (contentType.getType().equals(APPLICATION_CLOUDEVENTS.getType()) && contentType
					.getSubtype().startsWith(APPLICATION_CLOUDEVENTS.getSubtype())) {

				String dataContentType = StringUtils.hasText(inputContentType) ? inputContentType
						: MimeTypeUtils.APPLICATION_JSON_VALUE;

				String suffix = contentType.getSubtypeSuffix();
				MimeType cloudEventDeserializationContentType = MimeTypeUtils
						.parseMimeType(contentType.getType() + "/" + suffix);
				Message<?> cloudEventMessage = MessageBuilder.fromMessage(inputMessage)
						.setHeader(MessageHeaders.CONTENT_TYPE, cloudEventDeserializationContentType)
						.setHeader(DATACONTENTTYPE, dataContentType).build();
				Map<String, Object> structuredCloudEvent = (Map<String, Object>) messageConverter
						.fromMessage(cloudEventMessage, Map.class);

				canonicalizeHeaders(structuredCloudEvent);
				Message<?> binaryCeMessage = buildBinaryMessageFromStructuredMap(structuredCloudEvent,
						inputMessage.getHeaders());

				return binaryCeMessage;
			}
		}
		else if (StringUtils.hasText(inputContentType)) { // this needs thinking since . .
			return MessageBuilder.fromMessage(inputMessage).setHeader(MessageHeaders.CONTENT_TYPE, inputContentType)
					.build();
		}
		return inputMessage;
	}


	/**
	 * Determines attribute prefix based on the presence of certain well defined headers.
	 *
	 * TODO work in progress as it needs to be refined
	 *
	 * @param messageHeaders map of message headers
	 * @return prefix (e.g., 'ce_' or 'ce-' etc.)
	 */
	protected static String determinePrefixToUse(Map<String, Object> messageHeaders) {
		Set<String> keys = messageHeaders.keySet();
		if (keys.contains("user-agent")) {
			return HTTP_ATTR_PREFIX;
		}
		else {
			for (String key : messageHeaders.keySet()) {
				if (key.startsWith("kafka_")) {
					return DEFAULT_ATTR_PREFIX;
				}
				else if (key.startsWith("amqp_")) {
					return AMQP_ATTR_PREFIX;
				}
				else if (key.startsWith(DEFAULT_ATTR_PREFIX)) {
					return DEFAULT_ATTR_PREFIX;
				}
				else if (key.startsWith(HTTP_ATTR_PREFIX)) {
					return HTTP_ATTR_PREFIX;
				}
				else if (key.startsWith(AMQP_ATTR_PREFIX)) {
					return AMQP_ATTR_PREFIX;
				}
			}
		}

		return "";
	}

	/**
	 * Will check for the existence of required attributes. Assumes attributes (headers)
	 * are in canonical form.
	 * @param message input {@link Message}
	 * @return true if this Message represents Cloud Event in binary-mode
	 */
	protected static boolean isBinary(Message<?> message) {
		return message.getHeaders().containsKey(SPECVERSION)
				&& message.getHeaders().containsKey(TYPE)
				&& message.getHeaders().containsKey(SOURCE);
	}

	/**
	 * Will canonicalize Cloud Event attributes (headers) by removing well known prefixes.
	 * So, for example 'ce_source' will become 'source'.
	 * @param headers message headers
	 */
	private static void canonicalizeHeaders(Map<String, Object> headers) {
		String[] keys = headers.keySet().toArray(new String[] {});
		for (String key : keys) {
			if (key.startsWith(HTTP_ATTR_PREFIX)) {
				Object value = headers.remove(key);
				key = key.substring(HTTP_ATTR_PREFIX.length());
				headers.put(key, value);
			}
			else if (key.startsWith(DEFAULT_ATTR_PREFIX)) {
				Object value = headers.remove(key);
				key = key.substring(DEFAULT_ATTR_PREFIX.length());
				headers.put(key, value);
			}
			else if (key.startsWith(AMQP_ATTR_PREFIX)) {
				Object value = headers.remove(key);
				key = key.substring(AMQP_ATTR_PREFIX.length());
				headers.put(key, value);
			}
		}
	}

	private static Message<?> buildBinaryMessageFromStructuredMap(Map<String, Object> structuredCloudEvent,
			MessageHeaders originalHeaders) {
		Object payload = structuredCloudEvent.remove(DATA);
		if (payload == null) {
			payload = Collections.emptyMap();
		}

		CloudEventMessageBuilder<?> messageBuilder = CloudEventMessageBuilder
				.withData(payload)
				.copyHeaders(structuredCloudEvent);

		for (String key : originalHeaders.keySet()) {
			if (!MessageHeaders.ID.equals(key)) {
				messageBuilder.setHeader(key, originalHeaders.get(key));
			}
		}

		return messageBuilder.build();
	}

	private static URI safeGetURI(Map<String, Object> map, String key) {
		Object uri = map.get(key);
		if (uri != null && uri instanceof String) {
			uri = URI.create((String) uri);
		}
		return (URI) uri;
	}
}
