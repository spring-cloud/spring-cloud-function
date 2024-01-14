/*
 * Copyright 2020-2022 the original author or authors.
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
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonMappingException;

import org.springframework.cloud.function.cloudevent.CloudEventMessageUtils;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of {@link MessageConverter} which uses Jackson or Gson libraries to do the
 * actual conversion via {@link JsonMapper} instance.
 *
 * @author Oleg Zhurakousky
 * @author Andrey Shlykov
 *
 * @since 3.0.4
 */
public class JsonMessageConverter extends AbstractMessageConverter {

	private final JsonMapper jsonMapper;

	public JsonMessageConverter(JsonMapper jsonMapper) {
		this(jsonMapper, new MimeType("application", "json"), new MimeType(CloudEventMessageUtils.APPLICATION_CLOUDEVENTS.getType(),
				CloudEventMessageUtils.APPLICATION_CLOUDEVENTS.getSubtype() + "+json"));
	}

	public JsonMessageConverter(JsonMapper jsonMapper, MimeType... supportedMimeTypes) {
		super(supportedMimeTypes);
		this.jsonMapper = jsonMapper;
	}

	@Override
	protected boolean supports(Class<?> clazz) {
		// should not be called, since we override canConvertFrom/canConvertTo instead
		throw new UnsupportedOperationException();
	}

	@Override
	protected boolean canConvertTo(Object payload, @Nullable MessageHeaders headers) {
		if (!supportsMimeType(headers)) {
			return false;
		}
		return true;
	}

	@Override
	protected boolean canConvertFrom(Message<?> message, @Nullable Class<?> targetClass) {
		return supportsMimeType(message.getHeaders()) && this.canDiscoverConvertToType(message, targetClass);
	}

	private boolean canDiscoverConvertToType(Message<?> message, Class<?> targetClass) {
		if (targetClass == null || targetClass == Object.class) {
			MimeType mimeType = getMimeType(message.getHeaders());
			if (StringUtils.hasText(mimeType.getParameter("type"))) {
				return true;
			}
			return false;
		}
		return true;
	}

	@Override
	protected Object convertFromInternal(Message<?> message, Class<?> targetClass, @Nullable Object conversionHint) {
		if (conversionHint instanceof ParameterizedTypeReference<?>) {
			conversionHint = ((ParameterizedTypeReference<?>) conversionHint).getType();
		}
		Type convertToType = this.getResolvedType(targetClass, conversionHint);
		if (convertToType == null || convertToType == Object.class) {
			MimeType mimeType = getMimeType(message.getHeaders());
			String type = mimeType.getParameter("type");
			if (StringUtils.hasText(type)) {
				try {
					convertToType = Thread.currentThread().getContextClassLoader().loadClass(type);
				}
				catch (ClassNotFoundException e) {
					throw new IllegalArgumentException("Failed to load class `" + type + "` specified by the provided content-type: " + mimeType, e);
				}
			}
			else {
				return message.getPayload();
			}
		}
		if (targetClass == byte[].class && message.getPayload() instanceof String) {
			return ((String) message.getPayload()).getBytes(StandardCharsets.UTF_8);
		}
		else {
			try {
				return this.jsonMapper.fromJson(message.getPayload(), convertToType);
			}
			catch (Exception e) {
				if (message.getPayload() instanceof byte[] && String.class.isAssignableFrom(targetClass)) {
					return new String((byte[]) message.getPayload(), StandardCharsets.UTF_8);
				}
				// SmartB Modification
				// force message conversion error propagation
				else if ("application/json".equals(message.getHeaders().get("Content-Type")) && e.getCause() instanceof JsonMappingException) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error parsing json", e.getCause());
				}
				// SmartB End Of Modification
				else if (logger.isDebugEnabled()) {
					Object payload = message.getPayload();
					if (payload instanceof byte[]) {
						payload = new String((byte[]) payload, StandardCharsets.UTF_8);
					}

					if (logger.isDebugEnabled()) {
						logger.debug("Failed to convert value: " + payload + " to: " + targetClass, e);
					}
					else {
						logger.warn("Failed to convert value: " + payload + " to: " + targetClass);
					}
				}
			}
		}

		return null;
	}

	@Override
	protected Object convertToInternal(Object payload, @Nullable MessageHeaders headers,
			@Nullable Object conversionHint) {
		return jsonMapper.toJson(payload);
	}

	private Type getResolvedType(Class<?> targetClass, @Nullable Object conversionHint) {
		if (conversionHint instanceof MethodParameter param) {
			param = param.nestedIfOptional();
			if (Message.class.isAssignableFrom(param.getParameterType())) {
				param = param.nested();
			}
			Type genericParameterType = param.getNestedGenericParameterType();
			Class<?> contextClass = param.getContainingClass();
			return GenericTypeResolver.resolveType(genericParameterType, contextClass);
		}
		else if (conversionHint instanceof ParameterizedType) {
			return (Type) conversionHint;
		}
		return targetClass;
	}

}
