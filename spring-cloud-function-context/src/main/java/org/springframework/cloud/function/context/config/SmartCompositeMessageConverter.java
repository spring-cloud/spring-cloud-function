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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.SmartMessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * FIX SMARTB - https://github.com/spring-cloud/spring-cloud-function/issues/901.
 * Try to return an error instead of log a warnin when an object can't be parsed
 * @author Oleg Zhurakousky
 * @author Salvatore Bernardo
 *
 */
public class SmartCompositeMessageConverter extends CompositeMessageConverter {

	private Log logger = LogFactory.getLog(this.getClass());

	public SmartCompositeMessageConverter(Collection<MessageConverter> converters) {
		super(converters);
	}

	@Override
	@Nullable
	public Object fromMessage(Message<?> message, Class<?> targetClass) {
		for (MessageConverter converter : getConverters()) {
			if (!(message.getPayload() instanceof byte[]) && targetClass.isInstance(message.getPayload()) && !(message.getPayload() instanceof Collection<?>)) {
				return message.getPayload();
			}
			try {
				Object result = converter.fromMessage(message, targetClass);
				if (result != null) {
					return result;
				}
			}
			// SmartB Modification
			// force message conversion error propagation
			catch (ResponseStatusException e) {
				throw e;
			}
			// SmartB End Of Modification
			catch (Exception e) {
				if (logger.isWarnEnabled()) {
					logger.warn("Failure during type conversion by " + converter + ". Will try the next converter.", e);
				}
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Object fromMessage(Message<?> message, Class<?> targetClass, @Nullable Object conversionHint) {
		if (!(message.getPayload() instanceof byte[]) && targetClass.isInstance(message.getPayload()) && !(message.getPayload() instanceof Collection<?>)) {
			return message.getPayload();
		}
		Object result = null;
		if (message.getPayload() instanceof Iterable && conversionHint != null) {
			Iterable<Object> iterablePayload = (Iterable<Object>) message.getPayload();
			Type genericItemType = FunctionTypeUtils.getImmediateGenericType((Type) conversionHint, 0);
			Class<?> genericItemRawType = FunctionTypeUtils.getRawType(genericItemType);
			List<Object> resultList = new ArrayList<>();
			for (Object item : iterablePayload) {
				boolean isConverted = false;
				if (item.getClass().getName().startsWith("org.springframework.kafka.support.KafkaNull")) {
					resultList.add(null);
					isConverted = true;
				}
				for (Iterator<MessageConverter> iterator = getConverters().iterator(); iterator.hasNext() && !isConverted;) {
					MessageConverter converter = (MessageConverter) iterator.next();
					if (!converter.getClass().getName().endsWith("ApplicationJsonMessageMarshallingConverter")) { // TODO Stream stuff, needs to be removed
						Message<?> m  = MessageBuilder.withPayload(item).copyHeaders(message.getHeaders()).build(); // TODO Message creating may be expensive
						Object conversionResult = (converter instanceof SmartMessageConverter & genericItemRawType != genericItemType ?
								((SmartMessageConverter) converter).fromMessage(m, genericItemRawType, genericItemType) :
								converter.fromMessage(m, genericItemRawType));
						if (conversionResult != null) {
							resultList.add(conversionResult);
							isConverted = true;
						}
					}
				}
			}
			result = resultList;
		}
		else {
			for (MessageConverter converter : getConverters()) {
				if (!converter.getClass().getName().endsWith("ApplicationJsonMessageMarshallingConverter")) { // TODO Stream stuff, needs to be removed
					result = (converter instanceof SmartMessageConverter ?
							((SmartMessageConverter) converter).fromMessage(message, targetClass, conversionHint) :
							converter.fromMessage(message, targetClass));
					if (result != null) {
						return result;
					}
				}
			}
		}

		return result;
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
					if (converter instanceof AbstractMessageConverter) {
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
