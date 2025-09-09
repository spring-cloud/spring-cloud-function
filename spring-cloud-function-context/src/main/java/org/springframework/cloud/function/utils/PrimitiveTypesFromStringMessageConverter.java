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

package org.springframework.cloud.function.utils;

import java.nio.charset.StandardCharsets;

import org.springframework.core.convert.ConversionService;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.util.MimeType;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
public class PrimitiveTypesFromStringMessageConverter extends AbstractMessageConverter {


	private final ConversionService conversionService;

	public PrimitiveTypesFromStringMessageConverter(ConversionService conversionService) {
		super(new MimeType("text", "plain"));
		this.conversionService = conversionService;
	}


	@Override
	protected boolean supports(Class<?> clazz) {
		return (Integer.class == clazz || Long.class == clazz);
	}

	@Override
	protected Object convertFromInternal(Message<?> message, Class<?> targetClass, @Nullable Object conversionHint) {
		return conversionService.convert(message.getPayload(), targetClass);
	}

	@Override
	@Nullable
	protected Object convertToInternal(Object payload, @Nullable MessageHeaders headers, @Nullable Object conversionHint) {
		return payload.toString().getBytes(StandardCharsets.UTF_8);
	}
}
