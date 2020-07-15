/*
 * Copyright 2019-2020 the original author or authors.
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

package org.springframework.cloud.function.context.config;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.stream.Collectors;

import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.SmartMessageConverter;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;

/**
 * A {@link org.springframework.messaging.converter.AbstractMessageConverter} wrapper that supports the concept of wildcard
 * negotiation when <em>producing</em> messages. To that effect, messages should contain an "accept" header, that may
 * contain a wildcard type (such as {@code text/*}, which may be tested against every
 * {@link AbstractMessageConverter#getSupportedMimeTypes() supported mime type} of the delegate MessageConverter.
 *
 * @author Eric Bottard
 * @author Oleg Zhurakousky
 */
public final class NegotiatingMessageConverterWrapper implements SmartMessageConverter {

	/**
	 * The Message Header key that may contain the list of (possibly wildcard) MimeTypes to convert to.
	 */
	public static final String ACCEPT = "accept";

	private final AbstractMessageConverter delegate;

	private NegotiatingMessageConverterWrapper(AbstractMessageConverter delegate) {
		this.delegate = delegate;
	}

	public static NegotiatingMessageConverterWrapper wrap(AbstractMessageConverter delegate) {
		return new NegotiatingMessageConverterWrapper(delegate);
	}

	@Override
	public Object fromMessage(Message<?> message, Class<?> targetClass) {
		return fromMessage(message, targetClass, null);
	}

	private boolean isJsonContentType(Message<?> message) {
		Object ct = message.getHeaders().get(MessageHeaders.CONTENT_TYPE);
		if (ct != null) {
			ct = ct.toString();
			return ((String) ct).startsWith("application/json");
		}
		return false;
	}

	@Override
	public Object fromMessage(Message<?> message, Class<?> targetClass, Object conversionHint) {
		if (!this.isJsonContentType(message) && message.getPayload() instanceof Collection) {
			Collection<?> collection = ((Collection<?>) message.getPayload()).stream()
					.map(value -> {
						try {
							Message<?> m = new Message<Object>() {
								@Override
								public Object getPayload() {
									return value;
								}

								@Override
								public MessageHeaders getHeaders() {
									return message.getHeaders();
								}
							};
							if (conversionHint != null && conversionHint instanceof ParameterizedType) {
								Type tClass = FunctionTypeUtils.getImmediateGenericType((ParameterizedType) conversionHint, 0);
								if (byte[].class.isAssignableFrom((Class<?>) tClass)) {
									return message;
								}
								return delegate.fromMessage(m, (Class<?>) tClass);
							}

							return delegate.fromMessage(m, targetClass, conversionHint);
						}
						catch (Exception e) {
							e.printStackTrace();
							//logger.error("Failed to convert payload " + value, e);
						}
						return null;
					}).filter(v -> v != null).collect(Collectors.toList());
			return CollectionUtils.isEmpty(collection) ? null : collection;
		}
		return delegate.fromMessage(message, targetClass, conversionHint);
	}

	@Override
	public Message<?> toMessage(Object payload, MessageHeaders headers, Object conversionHint) {
		MimeType accepted = headers.get(ACCEPT, MimeType.class);
		MessageHeaderAccessor accessor = new MessageHeaderAccessor();
		accessor.copyHeaders(headers);
		accessor.removeHeader(ACCEPT);
		// Fall back to (concrete) 'contentType' header if 'accept' is not present.
		// MimeType.includes() below should then amount to equality.
		if (accepted == null) {
			accepted = headers.get(MessageHeaders.CONTENT_TYPE, MimeType.class);
		}

		if (accepted != null) {
			Message<?> result = null;
			for (MimeType supportedConcreteType : delegate.getSupportedMimeTypes()) {
				if (supportedConcreteType.isWildcardType() || supportedConcreteType.isWildcardSubtype()) {
					result = delegate.toMessage(payload, accessor.toMessageHeaders(), conversionHint);
				}
				if (result == null && accepted.includes(supportedConcreteType)) {
					// Note the use of setHeader() which will set the value even if already present.
					accessor.setHeader(MessageHeaders.CONTENT_TYPE, supportedConcreteType);
					result = delegate.toMessage(payload, accessor.toMessageHeaders(), conversionHint);
				}
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

	@Override
	public Message<?> toMessage(Object payload, MessageHeaders headers) {
		return toMessage(payload, headers, null);
	}
}
