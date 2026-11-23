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

package org.springframework.cloud.function.context.config;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.SmartMessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;

/**
 *
 * @author Oleg Zhurakousky
 * @author Salvatore Bernardo
 *
 */
public class SmartCompositeMessageConverter extends CompositeMessageConverter {

	private final Log logger = LogFactory.getLog(this.getClass());

	private final Supplier<Collection<MessageConverterHelper>> messageConverterHelpersSupplier;

	public SmartCompositeMessageConverter(Collection<MessageConverter> converters) {
		this(converters, null);
	}

	public SmartCompositeMessageConverter(Collection<MessageConverter> converters, Supplier<Collection<MessageConverterHelper>> messageConverterHelpersSupplier) {
		super(converters);
		this.messageConverterHelpersSupplier = messageConverterHelpersSupplier;
	}

	@Override
	@Nullable
	public Object fromMessage(Message<?> message, Class<?> targetClass) {
		Collection<MessageConverterHelper> messageConverterHelpers = this.messageConverterHelpersSupplier != null
				? this.messageConverterHelpersSupplier.get() : Collections.emptyList();
		for (MessageConverter converter : snapshotConverters()) {
			if (!(message.getPayload() instanceof byte[]) && targetClass.isInstance(message.getPayload()) && !(message.getPayload() instanceof Collection<?>)) {
				return message.getPayload();
			}
			try {
				Object result = converter.fromMessage(message, targetClass);
				if (result != null) {
					return result;
				}
			}
			catch (Exception e) {
				this.logConversionFailure(converter, e);
				this.failConversionIfNecessary(message, messageConverterHelpers, e);
			}
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Object fromMessage(Message<?> message, Class<?> targetClass, @Nullable Object conversionHint) {
		Collection<MessageConverterHelper> messageConverterHelpers = this.messageConverterHelpersSupplier != null
				? this.messageConverterHelpersSupplier.get() : Collections.emptyList();
		if (!(message.getPayload() instanceof byte[]) && targetClass.isInstance(message.getPayload()) && !(message.getPayload() instanceof Collection<?>)) {
			return message.getPayload();
		}
		Object result = null;
		if (message.getPayload() instanceof Iterable && conversionHint != null) {
			Iterable<Object> iterablePayload = (Iterable<Object>) message.getPayload();
			Type genericItemType = FunctionTypeUtils.getImmediateGenericType((Type) conversionHint, 0);
			Class<?> genericItemRawType = FunctionTypeUtils.getRawType(genericItemType);
			List<Object> resultList = new ArrayList<>();
			List<MessageConverter> convertersSnapshot = snapshotConverters();
			for (Object item : iterablePayload) {
				boolean isConverted = false;
				if (item == null || item.getClass().getName().startsWith("org.springframework.kafka.support.KafkaNull")) {
					resultList.add(null);
					isConverted = true;
				}
				for (Iterator<MessageConverter> iterator = convertersSnapshot.iterator(); iterator.hasNext() && !isConverted;) {
					MessageConverter converter = iterator.next();
					if (!converter.getClass().getName().endsWith("ApplicationJsonMessageMarshallingConverter")) { // TODO Stream stuff, needs to be removed
						Message<?> m  = MessageBuilder.withPayload(item).copyHeaders(message.getHeaders()).build(); // TODO Message creating may be expensive
						try {
							Object conversionResult = (converter instanceof SmartMessageConverter smartConverter && genericItemRawType != genericItemType ?
									smartConverter.fromMessage(m, genericItemRawType, genericItemType) :
									converter.fromMessage(m, genericItemRawType));
							if (conversionResult != null) {
								resultList.add(conversionResult);
								isConverted = true;
							}
						}
						catch (Exception e) {
							this.logConversionFailure(converter, e);
							this.failConversionIfNecessary(message, messageConverterHelpers, e);
						}
					}
				}
				if (!isConverted) {
					this.postProcessBatchMessage(message, messageConverterHelpers, resultList.size());
					this.failConversionIfNecessary(message, messageConverterHelpers, null);
				}
			}
			return resultList;
		}
		else {
			for (MessageConverter converter : snapshotConverters()) {
				if (!converter.getClass().getName().endsWith("ApplicationJsonMessageMarshallingConverter")) { // TODO Stream stuff, needs to be removed
					try {
						result = (converter instanceof SmartMessageConverter smartConverter ?
								smartConverter.fromMessage(message, targetClass, conversionHint) :
								converter.fromMessage(message, targetClass));
						if (result != null) {
							return result;
						}
					}
					catch (Exception e) {
						this.logConversionFailure(converter, e);
						this.failConversionIfNecessary(message, messageConverterHelpers, e);
					}
				}
			}
		}
		this.failConversionIfNecessary(message, messageConverterHelpers, null);
		return result;
	}

	private void logConversionFailure(MessageConverter converter, Exception e) {
		if (logger.isWarnEnabled()) {
			logger.warn("Failure during type conversion by " + converter + ". Will try the next converter.", e);
		}
	}

	private void failConversionIfNecessary(Message<?> message, Collection<MessageConverterHelper> messageConverterHelpers, Throwable t) {
		for (MessageConverterHelper messageConverterHelper : messageConverterHelpers) {
			if (messageConverterHelper.shouldFailIfCantConvert(message, t)) {
				throw new MessageConversionException("Failed to convert Message with id '"
						+ message.getHeaders().getId() + "' and content type '"
						+ message.getHeaders().get(MessageHeaders.CONTENT_TYPE)
						+ "'. None of the available Message converters were able to convert this Message");
			}
		}
	}

	private void postProcessBatchMessage(Message<?> message, Collection<MessageConverterHelper> messageConverterHelpers, int index) {
		for (MessageConverterHelper messageConverterHelper : messageConverterHelpers) {
			messageConverterHelper.postProcessBatchMessageOnFailure(message, index);
		}
	}

	@Override
	@Nullable
	public Message<?> toMessage(Object payload, @Nullable MessageHeaders headers) {
		String[] contentTypes = getContentTypes(headers);
		if (contentTypes == null) {
			return null;
		}
		for (MessageConverter converter : snapshotConverters()) {
			for (String contentType : contentTypes) {
				if (!MimeType.valueOf(contentType).isConcrete()) {
					if (converter instanceof AbstractMessageConverter abstractMessageConverter) {
						List<MimeType> supportedMimeTypes = abstractMessageConverter.getSupportedMimeTypes();
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
		String[] contentTypes = getContentTypes(headers);
		if (contentTypes == null) {
			return null;
		}
		for (MessageConverter converter : snapshotConverters()) {
			for (String contentType : contentTypes) {
				if (!MimeType.valueOf(contentType).isConcrete()) {
					if (converter instanceof AbstractMessageConverter abstractMessageConverter) {
						List<MimeType> supportedMimeTypes = abstractMessageConverter.getSupportedMimeTypes();
						for (MimeType supportedMimeType : supportedMimeTypes) {
							MessageHeaderAccessor h = new MessageHeaderAccessor();
							h.copyHeaders(headers);
							h.setHeader(MessageHeaders.CONTENT_TYPE, supportedMimeType);
							Message<?> result = toMessage(converter, payload, h.getMessageHeaders(), conversionHint);
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
					Message<?> result = toMessage(converter, payload, h.getMessageHeaders(), conversionHint);
					if (result != null) {
						return result;
					}
				}
			}
		}
		return null;
	}

	@Nullable
	private static Message<?> toMessage(MessageConverter converter, Object payload, MessageHeaders headers,
			@Nullable Object conversionHint) {
		return converter instanceof SmartMessageConverter smartConverter
				? smartConverter.toMessage(payload, headers, conversionHint)
				: converter.toMessage(payload, headers);
	}

	@Nullable
	private static String[] getContentTypes(@Nullable MessageHeaders headers) {
		Object value = headers != null ? headers.get(MessageHeaders.CONTENT_TYPE) : null;
		if (value == null) {
			return null;
		}
		return StringUtils.delimitedListToStringArray(value.toString(), ",");
	}

	private List<MessageConverter> snapshotConverters() {
		return new ArrayList<>(getConverters());
	}
}
