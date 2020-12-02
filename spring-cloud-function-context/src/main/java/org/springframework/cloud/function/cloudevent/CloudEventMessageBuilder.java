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

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

/**
 * Message builder which is aware of Cloud Event semantics.
 * It provides type-safe setters for v1.0 Cloud Event attributes while
 * supporting any version by exposing a convenient {@link #setHeader(String, Object)} method.
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

	@SuppressWarnings("unchecked")
	public static <T> CloudEventMessageBuilder<T> fromMessage(Message<?> message) {
		CloudEventMessageBuilder<T> builder = new CloudEventMessageBuilder<T>(new HashMap<>(message.getHeaders()));
		builder.data = (T) message.getPayload();
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

	public Message<T> build() {
		if (!this.headers.containsKey(CloudEventMessageUtils.SPECVERSION)) {
			this.headers.put(CloudEventMessageUtils.SPECVERSION, "1.0");
		}
		return this.doBuild();
	}

	public Message<T> build(String attributePrefixToUse) {
		String[] keys = this.headers.keySet().toArray(new String[] {});
		for (String key : keys) {
			Object value = this.headers.remove(key);
			this.headers.put(attributePrefixToUse + key, value);
		}
		if (!this.headers.containsKey(attributePrefixToUse + CloudEventMessageUtils.SPECVERSION)) {
			this.headers.put(attributePrefixToUse + CloudEventMessageUtils.SPECVERSION, "1.0");
		}
		return build();
	}

	private Message<T> doBuild() {
		this.headers.put("message-type", "cloudevent");
		CloudEventMessageHeaders headers = new CloudEventMessageHeaders(this.headers, this.getUUID(), null);
		GenericMessage<T> message = new GenericMessage<T>(data, headers);
		return message;
	}

	private UUID getUUID() {
		UUID id = null;
		if (this.headers.containsKey(CloudEventMessageUtils.ID)) {
			String stringId = this.headers.get(CloudEventMessageUtils.ID).toString();
			try {
				id = UUID.fromString(stringId);
			}
			catch (Exception e) {
				logger.info("Provided Cloud Event 'id' is not compatible with Message 'id' which is UUID, "
						+ "therefore Cloud Event 'id' will be written as '_id' message header");
				this.headers.put("_" + CloudEventMessageUtils.ID, stringId);
				this.headers.remove(CloudEventMessageUtils.ID);
			}
		}
		return id;
	}

	private static class CloudEventMessageHeaders extends MessageHeaders {

		/**
		 *
		 */
		private static final long serialVersionUID = -6424866731588545945L;

		protected CloudEventMessageHeaders(Map<String, Object> headers, UUID id, Long timestamp) {
			super(headers, id, timestamp);
		}

	}
}
