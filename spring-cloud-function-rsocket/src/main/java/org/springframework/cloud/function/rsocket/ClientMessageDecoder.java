/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.function.rsocket;

import java.lang.reflect.Type;
import java.util.Map;

import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.lang.Nullable;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 *
 */
class ClientMessageDecoder extends Jackson2JsonDecoder {

	private final JsonMapper jsonMapper;

	ClientMessageDecoder(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
		return true;
	}


	@SuppressWarnings("unchecked")
	@Override
	public Object decode(DataBuffer dataBuffer, ResolvableType targetType,
			@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) throws DecodingException {

		ResolvableType type = ResolvableType.forClassWithGenerics(Map.class, String.class, Object.class);
		Map<String, Object> messageMap = (Map<String, Object>) super.decode(dataBuffer, type, mimeType, hints);

		Type requestedType = FunctionTypeUtils.getGenericType(targetType.getType());
		Object payload = this.jsonMapper.fromJson(messageMap.get(FunctionRSocketUtils.PAYLOAD), requestedType);

		if (FunctionTypeUtils.isMessage(targetType.getType())) {
			return MessageBuilder.withPayload(payload)
				.copyHeaders((Map<String, ?>) messageMap.get(FunctionRSocketUtils.HEADERS))
				.build();
		}
		else {
			return payload;
		}
	}
}
