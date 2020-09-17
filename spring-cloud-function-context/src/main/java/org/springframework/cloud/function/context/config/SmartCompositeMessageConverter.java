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
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;

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
			MessageHeaderAccessor accessor = new MessageHeaderAccessor();
			accessor.copyHeaders(headers);
			if (this.isNotConcreteContentType(accessor, converter)) {
				List<MimeType> supportedMimeTypes = ((AbstractMessageConverter) converter).getSupportedMimeTypes();
				for (MimeType supportedMimeType : supportedMimeTypes) {
					accessor.setHeader(MessageHeaders.CONTENT_TYPE, supportedMimeType);
					Message<?> result = converter.toMessage(payload, accessor.getMessageHeaders());
					if (result != null) {
						return result;
					}
				}
			}
			else {
				Message<?> result = converter.toMessage(payload, headers);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

	@Override
	@Nullable
	public Message<?> toMessage(Object payload, @Nullable MessageHeaders headers, @Nullable Object conversionHint) {

		for (MessageConverter converter : getConverters()) {
			MessageHeaderAccessor accessor = new MessageHeaderAccessor();
			accessor.copyHeaders(headers);
			if (this.isNotConcreteContentType(accessor, converter)) {
				List<MimeType> supportedMimeTypes = ((AbstractMessageConverter) converter).getSupportedMimeTypes();
				for (MimeType supportedMimeType : supportedMimeTypes) {
					accessor.setHeader(MessageHeaders.CONTENT_TYPE, supportedMimeType);
					Message<?> result = ((AbstractMessageConverter) converter).toMessage(payload, accessor.getMessageHeaders(), conversionHint);
					if (result != null) {
						return result;
					}
				}
			}
			else {
				Message<?> result = ((AbstractMessageConverter) converter).toMessage(payload, headers, conversionHint);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

	private boolean isNotConcreteContentType(MessageHeaderAccessor accessor, MessageConverter converter) {
		return !accessor.getContentType().isConcrete() && converter instanceof AbstractMessageConverter
				&& !CollectionUtils.isEmpty(((AbstractMessageConverter) converter).getSupportedMimeTypes());
	}
}
