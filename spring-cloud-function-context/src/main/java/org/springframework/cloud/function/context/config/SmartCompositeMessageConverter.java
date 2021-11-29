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

package org.springframework.cloud.function.context.config;

import java.util.Collection;
import java.util.List;

import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.SmartMessageConverter;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class SmartCompositeMessageConverter extends CompositeMessageConverter {

	public SmartCompositeMessageConverter(Collection<MessageConverter> converters) {
		super(converters);
	}

	@Override
	@Nullable
	public Object fromMessage(Message<?> message, Class<?> targetClass) {
		for (MessageConverter converter : getConverters()) {
			Object result = converter.fromMessage(message, targetClass);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	@Override
	@Nullable
	public Object fromMessage(Message<?> message, Class<?> targetClass, @Nullable Object conversionHint) {
		for (MessageConverter converter : getConverters()) {
			Object result = (converter instanceof SmartMessageConverter ?
					((SmartMessageConverter) converter).fromMessage(message, targetClass, conversionHint) :
					converter.fromMessage(message, targetClass));
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	@Override
	@Nullable
	public Message<?> toMessage(Object payload, @Nullable MessageHeaders headers) {
		for (MessageConverter converter : getConverters()) {
			if (headers.get(MessageHeaders.CONTENT_TYPE) == null) {
				return null;
			}

			Object value = headers.get(MessageHeaders.CONTENT_TYPE).toString();
			String[] contentTypes = StringUtils.delimitedListToStringArray((String) value, ",");
			for (String contentType : contentTypes) {
				if (!MimeType.valueOf(contentType).isConcrete()) {
					List<MimeType> supportedMimeTypes = ((AbstractMessageConverter) converter).getSupportedMimeTypes();
					for (MimeType supportedMimeType : supportedMimeTypes) {
						if (supportedMimeType.isCompatibleWith(MimeType.valueOf(contentType))) {
							MessageHeaderAccessor h = new MessageHeaderAccessor();
							h.copyHeaders(headers);
							h.setHeader(MessageHeaders.CONTENT_TYPE, supportedMimeType);
							Message<?> result = converter.toMessage(payload, h.getMessageHeaders());
							if (result != null) {
								return result;
							}
						}
					}
				}
				else {
					MessageHeaderAccessor h = new MessageHeaderAccessor();
					h.copyHeaders(headers);
					h.setHeader(MessageHeaders.CONTENT_TYPE, contentType);
					Message<?> result = converter.toMessage(payload, h.getMessageHeaders());
					if (result != null) {
						return result;
					}
				}
			}
		}
		return null;
	}

	@Override
	@Nullable
	public Message<?> toMessage(Object payload, @Nullable MessageHeaders headers, @Nullable Object conversionHint) {
		for (MessageConverter converter : getConverters()) {
			Object value = headers.get(MessageHeaders.CONTENT_TYPE).toString();
			String[] contentTypes = StringUtils.delimitedListToStringArray((String) value, ",");
			for (String contentType : contentTypes) {
				if (!MimeType.valueOf(contentType).isConcrete()) {
					List<MimeType> supportedMimeTypes = ((AbstractMessageConverter) converter).getSupportedMimeTypes();
					for (MimeType supportedMimeType : supportedMimeTypes) {
						MessageHeaderAccessor h = new MessageHeaderAccessor();
						h.copyHeaders(headers);
						h.setHeader(MessageHeaders.CONTENT_TYPE, supportedMimeType);
						Message<?> result = ((SmartMessageConverter) converter).toMessage(payload, h.getMessageHeaders(), conversionHint);
						if (result != null) {
							return result;
						}
					}
				}
				else {
					MessageHeaderAccessor h = new MessageHeaderAccessor();
					h.copyHeaders(headers);
					h.setHeader(MessageHeaders.CONTENT_TYPE, contentType);
					Message<?> result = ((SmartMessageConverter) converter).toMessage(payload, h.getMessageHeaders(), conversionHint);
					if (result != null) {
						return result;
					}
				}
			}
		}
		return null;
	}
}
