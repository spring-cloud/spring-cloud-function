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

import java.net.URI;
import java.time.OffsetTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.context.message.MessageUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Message builder which is aware of Cloud Event semantics.
 * It provides type-safe setters for v1.0 Cloud Event attributes while
 * supporting all other versions via convenient
 * {@link #setHeader(String, Object)} method.
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
public final class CloudEventMessageBuilder<T> {

	protected Log logger = LogFactory.getLog(this.getClass());

	private final Map<String, Object> headers;

	private T data;

	private CloudEventMessageBuilder(Map<String, Object> headers) {
		this.headers = headers == null ? new HashMap<>() : headers;
	}

	public static <T> CloudEventMessageBuilder<T> withData(T data) {
		CloudEventMessageBuilder<T> builder = new CloudEventMessageBuilder<T>(null);
		builder.data = data;
		return builder;
	}

	public static <T> CloudEventMessageBuilder<T> fromMessage(Message<T> message) {
		CloudEventMessageBuilder<T> builder = new CloudEventMessageBuilder<T>(new HashMap<>(message.getHeaders()));
		builder.data = message.getPayload();
		return builder;
	}

	public CloudEventMessageBuilder<T> setId(String id) {
		this.headers.put(CloudEventMessageUtils.ID, id);
		return this;
	}

	public CloudEventMessageBuilder<T> setSource(URI uri) {
		this.headers.put(CloudEventMessageUtils.SOURCE, uri);
		return this;
	}

	public CloudEventMessageBuilder<T> setSource(String uri) {
		this.headers.put(CloudEventMessageUtils.SOURCE, URI.create(uri));
		return this;
	}

	public CloudEventMessageBuilder<T> setSpecVersion(String specversion) {
		this.headers.put(CloudEventMessageUtils.SPECVERSION, specversion);
		return this;
	}

	public CloudEventMessageBuilder<T> setType(String type) {
		this.headers.put(CloudEventMessageUtils.TYPE, type);
		return this;
	}

	public CloudEventMessageBuilder<T> setDataContentType(String dataContentType) {
		this.headers.put(CloudEventMessageUtils.DATACONTENTTYPE, dataContentType);
		return this;
	}

	public CloudEventMessageBuilder<T> setDataSchema(URI dataSchema) {
		this.headers.put(CloudEventMessageUtils.DATASCHEMA, dataSchema);
		return this;
	}

	public CloudEventMessageBuilder<T> setDataSchema(String dataSchema) {
		this.headers.put(CloudEventMessageUtils.DATASCHEMA, URI.create(dataSchema));
		return this;
	}

	public CloudEventMessageBuilder<T> setSubject(String subject) {
		this.headers.put(CloudEventMessageUtils.SUBJECT, subject);
		return this;
	}

	public CloudEventMessageBuilder<T> setTime(OffsetTime time) {
		this.headers.put(CloudEventMessageUtils.TIME, time);
		return this;
	}

	public CloudEventMessageBuilder<T> setTime(String time) {
		this.headers.put(CloudEventMessageUtils.TIME, OffsetTime.parse(time));
		return this;
	}

	public CloudEventMessageBuilder<T> setHeader(String key, Object value) {
		this.headers.put(key, value);
		return this;
	}

	public CloudEventMessageBuilder<T> copyHeaders(Map<String, Object> headers) {
		this.headers.putAll(headers);
		return this;
	}

	/**
	 * Returns a snapshot of the headers {@link Map} at the time this method is called.
	 * The returned Map is read-only.
	 *
	 * @return map of headers
	 */
	public Map<String, Object> toHeadersMap() {
		return Collections.unmodifiableMap(this.headers);
	}

	/**
	 * Will build the message ensuring that the Cloud Event attributes are all
	 * prefixed with the prefix determined by the framework. If you want to
	 * use a specific prefix please use {@link #build(String)} method.
	 * @return instance of {@link Message}
	 */
	public Message<T> build() {
		return this.doBuild(CloudEventMessageUtils.determinePrefixToUse(this.headers));
	}

	/**
	 * Will build the message ensuring that the Cloud Event attributes are
	 * prefixed with the 'attributePrefixToUse'.
	 *
	 * @param attributePrefixToUse prefix to use for attributes
	 * @return instance of {@link Message}
	 */
	public Message<T> build(String attributePrefixToUse) {
		Assert.isTrue(attributePrefixToUse.equals(CloudEventMessageUtils.DEFAULT_ATTR_PREFIX)
				|| attributePrefixToUse.equals(CloudEventMessageUtils.KAFKA_ATTR_PREFIX)
				|| attributePrefixToUse.equals(CloudEventMessageUtils.AMQP_ATTR_PREFIX), "Supported prefixes are "
				+ CloudEventMessageUtils.DEFAULT_ATTR_PREFIX
				+ ", " + CloudEventMessageUtils.KAFKA_ATTR_PREFIX
				+ ", " + CloudEventMessageUtils.AMQP_ATTR_PREFIX
				+ ". Was " + attributePrefixToUse);
		if (StringUtils.hasText(attributePrefixToUse)) {
			String[] keys = this.headers.keySet().toArray(new String[] {});
			for (String key : keys) {
				if (key.startsWith(CloudEventMessageUtils.DEFAULT_ATTR_PREFIX)) {
					this.swapPrefix(key, CloudEventMessageUtils.DEFAULT_ATTR_PREFIX, attributePrefixToUse);
				}
				else if (key.startsWith(CloudEventMessageUtils.AMQP_ATTR_PREFIX)) {
					this.swapPrefix(key, CloudEventMessageUtils.AMQP_ATTR_PREFIX, attributePrefixToUse);
				}
				else if (key.startsWith(CloudEventMessageUtils.KAFKA_ATTR_PREFIX)) {
					this.swapPrefix(key, CloudEventMessageUtils.KAFKA_ATTR_PREFIX, attributePrefixToUse);
				}
			}
		}
		return doBuild(attributePrefixToUse);
	}

	private void swapPrefix(String key, String currentPrefix, String newPrefix) {
		Object value = headers.remove(key);
		key = key.substring(currentPrefix.length());
		this.headers.put(newPrefix + key, value);
	}

	private Message<T> doBuild(String prefix) {
		if (!this.headers.containsKey(prefix + CloudEventMessageUtils._SPECVERSION)) {
			this.headers.put(prefix + CloudEventMessageUtils._SPECVERSION, "1.0");
		}
		if (!this.headers.containsKey(prefix + CloudEventMessageUtils._ID)) {
			this.headers.put(prefix + CloudEventMessageUtils._ID, UUID.randomUUID().toString());
		}
		this.headers.put(MessageUtils.MESSAGE_TYPE, CloudEventMessageUtils.CLOUDEVENT_VALUE);

		if (!this.headers.containsKey(prefix + CloudEventMessageUtils._TYPE)) {
			this.headers.put(prefix + CloudEventMessageUtils._TYPE, this.data.getClass().getName());
		}
		if (!this.headers.containsKey(prefix + CloudEventMessageUtils._SOURCE)) {
			this.headers.put(prefix + CloudEventMessageUtils._SOURCE, URI.create("https://spring.io/"));
		}
		MessageHeaders headers = new MessageHeaders(this.headers);
		GenericMessage<T> message = new GenericMessage<T>(this.data, headers);
		Assert.isTrue(CloudEventMessageUtils.isCloudEvent(message), "The message does not appear to be a valid Cloud Event, "
				+ "since one of the required attributes (id, specversion, type, source) is missing");
		return message;
	}
}
