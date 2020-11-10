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
import java.util.Map;

import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;

/**
 * Implementation of {@link MessageConverter} which uses Jackson or Gson libraries to do the
 * actual conversion via {@link JsonMapper} instance.
 *
 * @author Oleg Zhurakousky
 *
 * @since 3.1
 */
public class CloudEventJsonMessageConverter extends JsonMessageConverter {

	private final JsonMapper mapper;

	public CloudEventJsonMessageConverter(JsonMapper jsonMapper) {
		super(jsonMapper, new MimeType("application", "cloudevents+json"));
		this.mapper = jsonMapper;
	}

	@Override
	protected Object convertFromInternal(Message<?> message, Class<?> targetClass, @Nullable Object conversionHint) {
		if (this.isBinary(message)) {
			return super.convertFromInternal(message, targetClass, conversionHint);
		}
		else {
			Type convertToType = conversionHint == null ? targetClass : (Type) conversionHint;
			String jsonString = (String) message.getPayload();
			Map<String, Object> mapEvent = this.mapper.fromJson(jsonString, Map.class);
			Object payload = this.mapper.fromJson(this.mapper.toJson(mapEvent.get("data")), convertToType);
			mapEvent.remove("data");
			return MessageBuilder.withPayload(payload).copyHeaders(mapEvent).build();
		}
	}

	@Override
	protected Object convertToInternal(Object payload, @Nullable MessageHeaders headers,
			@Nullable Object conversionHint) {
		throw new UnsupportedOperationException("Temporarily not supported as this converter is work in progress");
	}

	private boolean isBinary(Message<?> message) {
		Map<String, Object> headers = message.getHeaders();
		return headers.containsKey("source") && headers.containsKey("specversion") && headers.containsKey("type");
	}
}
