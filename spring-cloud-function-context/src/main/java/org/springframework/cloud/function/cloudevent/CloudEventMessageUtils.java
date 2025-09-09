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

package org.springframework.cloud.function.cloudevent;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.cloud.function.context.message.MessageUtils;
import org.springframework.cloud.function.context.message.MessageUtils.MessageStructureWithCaseInsensitiveHeaderKeys;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.ContentTypeResolver;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

/**
 * Miscellaneous utility methods to assist with representing Cloud Event as Spring
 * {@link Message}. <br>
 * Primarily intended for the internal use within Spring-based frameworks and
 * integrations.
 *
 * @author Oleg Zhurakousky
 * @author Dave Syer
 * @author Chris Bono
 * @since 3.1
 */
public final class CloudEventMessageUtils {

	private static final ContentTypeResolver contentTypeResolver = new DefaultContentTypeResolver() {

		@Override
		public MimeType resolve(@Nullable MessageHeaders headers) {
			if (headers.containsKey("content-type")) { // this is temporary workaround for RSocket
				return MimeType.valueOf(headers.get("content-type").toString());
			}
			return super.resolve(headers);
		}

	};

	private CloudEventMessageUtils() {
	}

	//=========== INTERNAL USE ONLY ==
	static String _DATA = "data";

	static String _ID = "id";

	static String _SOURCE = "source";

	static String _SPECVERSION = "specversion";

	static String _TYPE = "type";

	static String _DATACONTENTTYPE = "datacontenttype";

	static String _DATASCHEMA = "dataschema";

	static String _SCHEMAURL = "schemaurl";

	static String _SUBJECT = "subject";

	static String _TIME = "time";
	// ================================

	/**
	 * String value of 'cloudevent'. Typically used as {@link MessageUtils#MESSAGE_TYPE}
	 */
	public static String CLOUDEVENT_VALUE = "cloudevent";

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
	public static String DEFAULT_ATTR_PREFIX = "ce-";

	/**
	 * AMQP attributes prefix.
	 */
	public static String AMQP_ATTR_PREFIX = "cloudEvents:";

	/**
	 * Prefix for attributes.
	 */
	public static String KAFKA_ATTR_PREFIX = "ce_";

	/**
	 * Value for 'data' attribute.
	 */
	public static String DATA = DEFAULT_ATTR_PREFIX + _DATA;

	/**
	 * Value for 'id' attribute.
	 */
	public static String ID = DEFAULT_ATTR_PREFIX + _ID;

	/**
	 * Value for 'source' attribute.
	 */
	public static String SOURCE = DEFAULT_ATTR_PREFIX + _SOURCE;

	/**
	 * Value for 'specversion' attribute.
	 */
	public static String SPECVERSION = DEFAULT_ATTR_PREFIX + _SPECVERSION;

	/**
	 * Value for 'type' attribute.
	 */
	public static String TYPE = DEFAULT_ATTR_PREFIX + _TYPE;

	/**
	 * Value for 'datacontenttype' attribute.
	 */
	public static String DATACONTENTTYPE = DEFAULT_ATTR_PREFIX + _DATACONTENTTYPE;

	/**
	 * Value for 'dataschema' attribute.
	 */
	public static String DATASCHEMA = DEFAULT_ATTR_PREFIX + _DATASCHEMA;

	/**
	 * V03 name for 'dataschema' attribute.
	 */
	public static final String SCHEMAURL = DEFAULT_ATTR_PREFIX + _SCHEMAURL;

	/**
	 * Value for 'subject' attribute.
	 */
	public static String SUBJECT = DEFAULT_ATTR_PREFIX + _SUBJECT;

	/**
	 * Value for 'time' attribute.
	 */
	public static String TIME = DEFAULT_ATTR_PREFIX + _TIME;


	public static String getId(Message<?> message) {
		String prefix = determinePrefixToUse(message.getHeaders());
		Object value = message.getHeaders().get(prefix + MessageHeaders.ID);
		if (value instanceof byte[] v) {
			value = toString(v);
		}
		return (String) value;
	}

	public static URI getSource(Message<?> message) {
		String prefix = determinePrefixToUse(message.getHeaders());
		return safeGetURI(message.getHeaders(), prefix + _SOURCE);
	}

	public static String getSpecVersion(Message<?> message) {
		String prefix = determinePrefixToUse(message.getHeaders());
		Object value = message.getHeaders().get(prefix + _SPECVERSION);
		if (value instanceof byte[] v) {
			value = toString(v);
		}
		return (String) value;
	}

	public static String getType(Message<?> message) {
		String prefix = determinePrefixToUse(message.getHeaders());
		Object value = message.getHeaders().get(prefix + _TYPE);
		if (value instanceof byte[] v) {
			value = toString(v);
		}
		return (String) value;
	}

	public static String getDataContentType(Message<?> message) {
		String prefix = determinePrefixToUse(message.getHeaders());
		Object value = message.getHeaders().get(prefix + _DATACONTENTTYPE);
		if (value instanceof byte[] v) {
			value = toString(v);
		}
		return (String) value;
	}

	public static URI getDataSchema(Message<?> message) {
		String prefix = determinePrefixToUse(message.getHeaders());
		return safeGetURI(message.getHeaders(), prefix + _DATASCHEMA);
	}

	public static String getSubject(Message<?> message) {
		String prefix = determinePrefixToUse(message.getHeaders());
		Object value = message.getHeaders().get(prefix + _SUBJECT);
		if (value instanceof byte[] v) {
			value = toString(v);
		}
		return (String) value;
	}

	public static OffsetDateTime getTime(Message<?> message) {
		String prefix = determinePrefixToUse(message.getHeaders());
		Object time = message.getHeaders().get(prefix + _TIME);
		return time instanceof String ? OffsetDateTime.parse((String) time) : null;
	}

	@SuppressWarnings("unchecked")
	public static <T> T getData(Message<?> message) {
		return (T) message.getPayload();
	}

	public static Map<String, Object> getAttributes(Message<?> message) {
		return message.getHeaders().entrySet().stream()
				.filter(e -> isAttribute(e.getKey()))
				.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
	}

	/**
	 * This method does several things.
	 * First it canonicalizes Cloud Events attributes ensuring that they are all prefixed
	 * with 'ce-' prefix regardless where they came from.
	 * It also transforms structured-mode Cloud Event to binary-mode and then canonicalizes attributes
	 * as well as described in the previous sentence.
	 */
	@SuppressWarnings("unchecked")
	static Message<?> toCanonical(Message<?> inputMessage, MessageConverter messageConverter) {
		inputMessage = canonicalizeHeadersWithPossibleCopy(inputMessage);
		Map<String, Object> headers = new HashMap<>(inputMessage.getHeaders());

		boolean isCloudEvent = isCloudEvent(inputMessage);
		if (isCloudEvent && headers.containsKey("content-type")) {
			inputMessage = MessageBuilder.fromMessage(inputMessage).setHeader(MessageHeaders.CONTENT_TYPE, headers.get("content-type")).build();
		}
		MimeType contentType = contentTypeResolver.resolve(inputMessage.getHeaders());
		String inputContentType = (String) inputMessage.getHeaders().get(DATACONTENTTYPE);
		// first check the obvious and see if content-type is `cloudevents`
		if (!isCloudEvent && contentType != null) {
			// structured-mode

			if (contentType.getType().equals(APPLICATION_CLOUDEVENTS.getType()) && contentType
					.getSubtype().startsWith(APPLICATION_CLOUDEVENTS.getSubtype())) {

				String dataContentType = StringUtils.hasText(inputContentType) ? inputContentType
						: MimeTypeUtils.APPLICATION_JSON_VALUE;

				String suffix = contentType.getSubtypeSuffix() == null ? "json" : contentType.getSubtypeSuffix();
				MimeType cloudEventDeserializationContentType = MimeTypeUtils
						.parseMimeType(contentType.getType() + "/" + suffix);
				Message<?> cloudEventMessage = MessageBuilder.fromMessage(inputMessage)
						.setHeader(MessageHeaders.CONTENT_TYPE, cloudEventDeserializationContentType)
						.setHeader(DATACONTENTTYPE, dataContentType).build();
				Map<String, Object> structuredCloudEvent = (Map<String, Object>) messageConverter
						.fromMessage(cloudEventMessage, Map.class);
				canonicalizeHeaders(structuredCloudEvent, true);
				return buildBinaryMessageFromStructuredMap(structuredCloudEvent,
						inputMessage.getHeaders());
			}
		}
		else if (StringUtils.hasText(inputContentType)) {
			// binary-mode, but DATACONTENTTYPE was specified explicitly so we set it as CT to ensure proper message converters are used.
			return MessageBuilder.fromMessage(inputMessage).setHeader(MessageHeaders.CONTENT_TYPE, inputContentType)
					.build();
		}
		return inputMessage;
	}

	/**
	 * Attempts to {@link #canonicalizeHeaders canonicalize} the headers of a message.
	 * @param message the message
	 * @return a copy of the message with the canonicalized headers or the passed in unmodified message if no
	 * headers were canonicalized
	 */
	// VisibleForTesting
	static Message<?> canonicalizeHeadersWithPossibleCopy(Message<?> message) {
		Map<String, Object> headers = new HashMap<>(message.getHeaders());
		boolean headersModified = canonicalizeHeaders(headers, false);
		if (headersModified) {
			message = MessageBuilder.fromMessage(message)
					.removeHeaders("*")
					.copyHeaders(headers)
					.build();
		}
		return message;
	}

	/**
	 * Will canonicalize Cloud Event attributes (headers) by ensuring canonical
	 * prefix for all attributes and extensions regardless of where they came from.
	 * The canonical prefix is 'ce-'.
	 *
	 * So, for example 'ce_source' will become 'ce-source'.
	 * @param headers message headers
	 * @param structured boolean signifying that headers map represents structured Cloud Event
	 * at which point attributes without any prefix will still be treated as
	 * Cloud Event attributes.
	 * @return whether the headers were modified during the process
	 */
	private static boolean canonicalizeHeaders(Map<String, Object> headers, boolean structured) {
		boolean modified = false;
		String[] keys = headers.keySet().toArray(new String[] {});
		for (String key : keys) {
			if (key.startsWith(DEFAULT_ATTR_PREFIX)) {
				Object value = headers.remove(key);
				String newKey = DEFAULT_ATTR_PREFIX + key.substring(DEFAULT_ATTR_PREFIX.length());
				headers.put(newKey, value);
				modified |= (!Objects.equals(key, newKey));
			}
			else if (key.startsWith(KAFKA_ATTR_PREFIX)) {
				Object value = headers.remove(key);
				key = key.substring(KAFKA_ATTR_PREFIX.length());
				headers.put(DEFAULT_ATTR_PREFIX + key, value);
				modified = true;
			}
			else if (key.startsWith(AMQP_ATTR_PREFIX)) {
				Object value = headers.remove(key);
				key = key.substring(AMQP_ATTR_PREFIX.length());
				headers.put(DEFAULT_ATTR_PREFIX + key, value);
				modified = true;
			}
			else if (structured) {
				Object value = headers.remove(key);
				headers.put(DEFAULT_ATTR_PREFIX + key, value);
				modified = true;
			}
		}
		return modified;
	}

	/**
	 * Determines attribute prefix based on the presence of certain well defined headers.
	 * @param messageHeaders map of message headers
	 * @return prefix (e.g., 'ce_' or 'ce-' etc.)
	 */
	static String determinePrefixToUse(Map<String, Object> messageHeaders) {
		return determinePrefixToUse(messageHeaders, false);
	}

	static String extractTargetProtocol(Map<String, Object> messageHeaders) {
		Iterator<String> keyIterator = messageHeaders.keySet().iterator();
		for (; keyIterator.hasNext();) {
			String key = keyIterator.next();
			if (key.startsWith("kafka_")) {
				return Protocols.KAFKA;
			}
			else if (key.startsWith("amqp")) {
				return Protocols.AMQP;
			}
		}
		return null;
	}

	static String determinePrefixToUse(Map<String, Object> messageHeaders, boolean strict) {
		String targetProtocol = extractTargetProtocol(messageHeaders);
		String prefix = determinePrefixToUse(targetProtocol);
		if (StringUtils.hasText(prefix) && (strict || StringUtils.hasText((String) messageHeaders.get(prefix + _SPECVERSION)))) {
			return prefix;
		}
		else {
			for (String key : messageHeaders.keySet()) {
				if (key.startsWith(DEFAULT_ATTR_PREFIX)) {
					return DEFAULT_ATTR_PREFIX;
				}
				else if (key.startsWith(KAFKA_ATTR_PREFIX)) {
					return KAFKA_ATTR_PREFIX;
				}
				else if (key.startsWith(AMQP_ATTR_PREFIX)) {
					return AMQP_ATTR_PREFIX;
				}
			}
		}

		return DEFAULT_ATTR_PREFIX;
	}

	/**
	 * Determines attribute prefix based on the provided target protocol.
	 * @param targetProtocol target protocol (see {@link MessageUtils#TARGET_PROTOCOL}
	 * @return prefix (e.g., 'ce_' or 'ce-' etc.)
	 */
	static String determinePrefixToUse(String targetProtocol) {
		if (StringUtils.hasText(targetProtocol)) {
			if (Protocols.KAFKA.equals(targetProtocol)) {
				return CloudEventMessageUtils.KAFKA_ATTR_PREFIX;
			}
			else if (Protocols.AMQP.equals(targetProtocol)) {
				return CloudEventMessageUtils.AMQP_ATTR_PREFIX;
			}
			else if (Protocols.HTTP.equals(targetProtocol)) {
				return CloudEventMessageUtils.DEFAULT_ATTR_PREFIX;
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
	public static boolean isCloudEvent(Message<?> message) {
		MessageStructureWithCaseInsensitiveHeaderKeys _message = MessageUtils.toCaseInsensitiveHeadersStructure(message);
		return (_message.getHeaders().containsKey(SPECVERSION)
					&& _message.getHeaders().containsKey(TYPE)
					&& _message.getHeaders().containsKey(SOURCE))
				||
				(_message.getHeaders().containsKey(_SPECVERSION)
						&& _message.getHeaders().containsKey(_TYPE)
						&& _message.getHeaders().containsKey(_SOURCE))
				||
				(_message.getHeaders().containsKey(AMQP_ATTR_PREFIX + _SPECVERSION)
					&& _message.getHeaders().containsKey(AMQP_ATTR_PREFIX + _TYPE)
					&& _message.getHeaders().containsKey(AMQP_ATTR_PREFIX + _SOURCE))
				||
				(_message.getHeaders().containsKey(KAFKA_ATTR_PREFIX + _SPECVERSION)
					&& _message.getHeaders().containsKey(KAFKA_ATTR_PREFIX + _TYPE)
					&& _message.getHeaders().containsKey(KAFKA_ATTR_PREFIX + _SOURCE));
	}

	private static boolean isAttribute(String key) {
		return key.startsWith(DEFAULT_ATTR_PREFIX) || key.startsWith(AMQP_ATTR_PREFIX) || key.startsWith(KAFKA_ATTR_PREFIX);
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
		if (uri != null) {
			if (uri instanceof String) {
				uri = URI.create((String) uri);
			}
			else if (uri instanceof byte[] u) {
				uri = URI.create(toString(u));
			}
		}
		return (URI) uri;
	}

	private static String toString(byte[] value) {
		return new String(value, StandardCharsets.UTF_8);
	}

	public static class Protocols {
		static String AMQP = "amqp";
		static String AVRO = "avro";
		static String HTTP = "http";
		static String JSON = "json";
		static String KAFKA = "kafka";
	}

}
