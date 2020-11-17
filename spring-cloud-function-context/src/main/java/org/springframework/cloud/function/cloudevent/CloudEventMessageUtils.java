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
import java.util.Set;
import java.util.UUID;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.ContentTypeResolver;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

/**
 * Miscellaneous utility methods to deal with Cloud Events - https://cloudevents.io/.
 * <br>
 * Primarily intended for the internal use within the framework;
 *
 * @author Oleg Zhurakousky
 * @author Dave Syer
 *
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
	 * Default attributes prefix which also suits Kafka.
	 */
	public static String DEFAULT_ATTR_PREFIX = "ce_";

	/**
	 * HTTP attributes prefix.
	 */
	public static String HTTP_ATTR_PREFIX = "ce-";

	/**
	 * AMQP attributes prefix.
	 */
	public static String AMQP_ATTR_PREFIX = "cloudEvents:";

	/**
	 * Value for 'data' attribute.
	 */
	public static String DATA = "data";

	/**
	 * Value for 'data' attribute with prefix.
	 */
	public static String CANONICAL_DATA = DEFAULT_ATTR_PREFIX + DATA;

	/**
	 * Value for 'id' attribute.
	 */
	public static String ID = "id";

	/**
	 * Value for 'id' attribute with prefix.
	 */
	public static String CANONICAL_ID = DEFAULT_ATTR_PREFIX + ID;

	/**
	 * Value for 'source' attribute.
	 */
	public static String SOURCE = "source";

	/**
	 * Value for 'source' attribute with prefix.
	 */
	public static String CANONICAL_SOURCE = DEFAULT_ATTR_PREFIX + SOURCE;

	/**
	 * Value for 'specversion' attribute.
	 */
	public static String SPECVERSION = "specversion";

	/**
	 * Value for 'specversion' attribute with prefix.
	 */
	public static String CANONICAL_SPECVERSION = DEFAULT_ATTR_PREFIX + SPECVERSION;

	/**
	 * Value for 'type' attribute.
	 */
	public static String TYPE = "type";

	/**
	 * Value for 'type' attribute with prefix.
	 */
	public static String CANONICAL_TYPE = DEFAULT_ATTR_PREFIX + TYPE;

	/**
	 * Value for 'datacontenttype' attribute.
	 */
	public static String DATACONTENTTYPE = "datacontenttype";

	/**
	 * Value for 'datacontenttype' attribute with prefix.
	 */
	public static String CANONICAL_DATACONTENTTYPE = DEFAULT_ATTR_PREFIX + DATACONTENTTYPE;

	/**
	 * Value for 'dataschema' attribute.
	 */
	public static String DATASCHEMA = "dataschema";

	/**
	 * Value for 'dataschema' attribute with prefix.
	 */
	public static String CANONICAL_DATASCHEMA = DEFAULT_ATTR_PREFIX + DATASCHEMA;

	/**
	 * Value for 'subject' attribute.
	 */
	public static String SUBJECT = "subject";

	/**
	 * Value for 'subject' attribute with prefix.
	 */
	public static String CANONICAL_SUBJECT = DEFAULT_ATTR_PREFIX + SUBJECT;

	/**
	 * Value for 'time' attribute.
	 */
	public static String TIME = "time";

	/**
	 * Value for 'time' attribute with prefix.
	 */
	public static String CANONICAL_TIME = DEFAULT_ATTR_PREFIX + TIME;

	/**
	 * Checks if {@link Message} represents cloud event in binary-mode.
	 */
	public static boolean isBinary(Map<String, Object> headers) {
		CloudEventAttributes attributes = new CloudEventAttributes(headers);
		return attributes.isValidCloudEvent();
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
		requiredAttributes.put(CloudEventMessageUtils.CANONICAL_ID, ce_id);
		requiredAttributes.put(CloudEventMessageUtils.CANONICAL_SPECVERSION, ce_specversion);
		requiredAttributes.put(CloudEventMessageUtils.CANONICAL_SOURCE, ce_source);
		requiredAttributes.put(CloudEventMessageUtils.CANONICAL_TYPE, ce_type);
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
	 * Will attempt to convert 'inputMessage' to a binary-mode Cloud Event {@link Message}.
	 * This typically happens when 'inputMessage' represents Cloud Event in structured-mode.
	 * <br>
	 * In the event the message already represents Cloud Event in binary-mode, or this
	 * message does not represent Cloud Event at all, it will be returned unchanged.
	 *
	 * @param inputMessage instance of incoming {@link Message}
	 * @param messageConverter instance of {@link MessageConverter} to assist with type conversion.
	 * @return instance of {@link Message} representing Cloud Event in binary-mode or unchanged 'inputMessage'.
	 */
	@SuppressWarnings("unchecked")
	public static Message<?> toBinary(Message<?> inputMessage, MessageConverter messageConverter) {

		Map<String, Object> headers = inputMessage.getHeaders();
		CloudEventAttributes attributes = new CloudEventAttributes(headers);

		// first check the obvious and see if content-type is `cloudevents`
		if (!attributes.isValidCloudEvent() && headers.containsKey(MessageHeaders.CONTENT_TYPE)) {
			MimeType contentType = contentTypeResolver.resolve(inputMessage.getHeaders());
			if (contentType.getType().equals(CloudEventMessageUtils.APPLICATION_CLOUDEVENTS.getType())
					&& contentType.getSubtype().startsWith(CloudEventMessageUtils.APPLICATION_CLOUDEVENTS.getSubtype())) {

				String dataContentType = StringUtils.hasText(attributes.getDataContentType())
						? attributes.getDataContentType()
						: MimeTypeUtils.APPLICATION_JSON_VALUE;

				String suffix = contentType.getSubtypeSuffix();
				Assert.hasText(suffix, "Content-type 'suffix' can not be determined from " + contentType);
				MimeType cloudEventDeserializationContentType = MimeTypeUtils
						.parseMimeType(contentType.getType() + "/" + suffix);
				Message<?> cloudEventMessage = MessageBuilder.fromMessage(inputMessage)
						.setHeader(MessageHeaders.CONTENT_TYPE, cloudEventDeserializationContentType)
						.setHeader(CloudEventMessageUtils.CANONICAL_DATACONTENTTYPE, dataContentType)
						.build();
				Map<String, Object> structuredCloudEvent = (Map<String, Object>) messageConverter.fromMessage(cloudEventMessage, Map.class);
				Message<?> binaryCeMessage = buildCeMessageFromStructured(structuredCloudEvent, inputMessage.getHeaders());
				return binaryCeMessage;
			}
		}
		else if (StringUtils.hasText(attributes.getDataContentType())) {
			return MessageBuilder.fromMessage(inputMessage)
				.setHeader(MessageHeaders.CONTENT_TYPE, attributes.getDataContentType())
				.build();
		}
		return inputMessage;
	}

	/**
	 * Will attempt to determine based on the headers the origin of Message (e.g., HTTP, Kafka etc)
	 * and based on this designate prefix to be used for Cloud Events attributes (i.e., `ce-` or `ce_` etc).
	 *
	 * @param messageHeaders instance of {@link MessageHeaders}
	 * @return prefix to be used for Cloud Events attributes
	 */
	public static String determinePrefixToUse(MessageHeaders messageHeaders) {
		Set<String> keys = messageHeaders.keySet();
		if (keys.contains("user-agent")) {
			return CloudEventMessageUtils.HTTP_ATTR_PREFIX;
		}
		else if (keys.contains("amqp")) {
			return CloudEventMessageUtils.AMQP_ATTR_PREFIX;
		}
		else {
			return CloudEventMessageUtils.DEFAULT_ATTR_PREFIX; // default which also suits Kafka 'ce_'
		}
	}

	/**
	 * Typically called by Consumer.

	 */
	public static CloudEventAttributes generateAttributes(Message<?> message, CloudEventAttributesProvider provider) {
		CloudEventAttributes attributes = generateDefaultAttributeValues(new CloudEventAttributes(message.getHeaders()),
				message.getPayload().getClass().getName().getClass().getName(), message.getPayload().getClass().getName().getClass().getName());
		provider.generateDefaultCloudEventHeaders(attributes);
		return attributes;
	}

	public static CloudEventAttributes generateAttributes(Message<?> inputMessage, String typeName, String sourceName) {
		CloudEventAttributes attributes = new CloudEventAttributes(inputMessage.getHeaders(), CloudEventMessageUtils.determinePrefixToUse(inputMessage.getHeaders()));
		return generateDefaultAttributeValues(attributes, typeName, sourceName);
	}

	private static Message<?> buildCeMessageFromStructured(Map<String, Object> structuredCloudEvent, MessageHeaders originalHeaders) {
		String prefixToUse = determinePrefixToUse(originalHeaders);
		Object data = null;
		if (structuredCloudEvent.containsKey(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.DATA)) {
			data = structuredCloudEvent.get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.DATA);
			structuredCloudEvent.remove(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.DATA);
		}
		else if (structuredCloudEvent.containsKey(CloudEventMessageUtils.CANONICAL_DATA)) {
			data = structuredCloudEvent.get(CloudEventMessageUtils.CANONICAL_DATA);
			structuredCloudEvent.remove(CloudEventMessageUtils.CANONICAL_DATA);
		}
		else if (structuredCloudEvent.containsKey(CloudEventMessageUtils.DATA)) {
			data = structuredCloudEvent.get(CloudEventMessageUtils.DATA);
			structuredCloudEvent.remove(CloudEventMessageUtils.DATA);
		}
		Assert.notNull(data, "'data' must not be null");
		MessageBuilder<?> builder = MessageBuilder.withPayload(data);
		CloudEventAttributes attributes = new CloudEventAttributes(structuredCloudEvent);
		builder.setHeader(prefixToUse + CloudEventMessageUtils.ID, attributes.getId());
		builder.setHeader(prefixToUse + CloudEventMessageUtils.SOURCE, attributes.getSource());
		builder.setHeader(prefixToUse + CloudEventMessageUtils.TYPE, attributes.getType());
		builder.setHeader(prefixToUse + CloudEventMessageUtils.SPECVERSION, attributes.getSpecversion());
		builder.copyHeaders(originalHeaders);
		return builder.build();
	}

	private static CloudEventAttributes generateDefaultAttributeValues(CloudEventAttributes attributes, String source, String type) {
		if (attributes.isValidCloudEvent()) {
			return attributes
					.setSpecversion("1.0")
					.setId(UUID.randomUUID().toString())
					.setType(type)
					.setSource(source);
		}
		return attributes;
	}
}
