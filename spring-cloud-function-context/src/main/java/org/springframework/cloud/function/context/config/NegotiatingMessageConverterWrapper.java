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

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.SmartMessageConverter;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.MimeType;

/**
 * A {@link org.springframework.messaging.converter.AbstractMessageConverter} wrapper that supports the concept of wildcard
 * negotiation when <em>producing</em> messages. To that effect, messages should contain an "accept" header, that may
 * contain a wildcard type (such as {@code text/*}, which may be tested against every
 * {@link AbstractMessageConverter#getSupportedMimeTypes() supported mime type} of the delegate MessageConverter.
 */
final class NegotiatingMessageConverterWrapper implements SmartMessageConverter {

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
	public Object fromMessage(Message<?> message, Class<?> targetClass, Object conversionHint) {
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
			for (MimeType supportedConcreteType : delegate.getSupportedMimeTypes()) {
				if (accepted.includes(supportedConcreteType)) {
					// Note the use of setHeader() which will set the value even if already present.
					accessor.setHeader(MessageHeaders.CONTENT_TYPE, supportedConcreteType);
					Message<?> result = delegate.toMessage(payload, accessor.toMessageHeaders(), conversionHint);
					if (result != null) {
						return result;
					}
				}
			}
		}
		return null;
	}

	@Override
	public Object fromMessage(Message<?> message, Class<?> targetClass) {
		return fromMessage(message, targetClass, null);
	}

	@Override
	public Message<?> toMessage(Object payload, MessageHeaders headers) {
		return toMessage(payload, headers, null);
	}
}
