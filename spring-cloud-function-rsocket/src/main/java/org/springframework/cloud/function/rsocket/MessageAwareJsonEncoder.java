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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractEncoder;
import org.springframework.core.codec.ByteArrayEncoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;



/**
 * @author Oleg Zhurakousky
 * @since 3.1
 *
 */
/*
 * We basically don't need it, but having it allows us not to depend on spring-web
 */
class MessageAwareJsonEncoder extends AbstractEncoder<Object> {

	private final JsonMapper mapper;

	private final boolean isClient;

	private final ByteArrayEncoder byteArrayEncoder;

	MessageAwareJsonEncoder(JsonMapper mapper) {
		this(mapper, false);
	}

	MessageAwareJsonEncoder(JsonMapper mapper, boolean isClient) {
		super(MimeTypeUtils.APPLICATION_JSON);
		this.mapper = mapper;
		this.isClient = isClient;
		this.byteArrayEncoder = new ByteArrayEncoder();
	}

	@Override
	public boolean canEncode(ResolvableType elementType, MimeType mimeType) {
		boolean canEncode = mimeType != null && mimeType.isCompatibleWith(MimeTypeUtils.APPLICATION_JSON);
		if (canEncode && this.isClient) {
			canEncode = (FunctionTypeUtils.isMessage(elementType.getType())
					|| Map.class.isAssignableFrom(FunctionTypeUtils.getRawType(elementType.getType())));
		}
		return canEncode;
	}


	@Override
	public List<MimeType> getEncodableMimeTypes() {
		return Collections.singletonList(MimeTypeUtils.APPLICATION_JSON);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public DataBuffer encodeValue(Object value, DataBufferFactory bufferFactory,
			ResolvableType valueType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

		if (value instanceof Message) {
			Object payload = ((Message<?>) value).getPayload();
			value = FunctionRSocketUtils.sanitizeMessageToMap((Message<?>) value);
			if (payload instanceof byte[]) {
				payload = new String((byte[]) payload, StandardCharsets.UTF_8); // safe for cases when we have JSON
				((Map) value).put(FunctionRSocketUtils.PAYLOAD, payload);
			}
		}
		else if (!(value instanceof Map)) {
			if (JsonMapper.isJsonString(value)) {
				value = this.mapper.fromJson(value, valueType.getType());
			}
			value = Collections.singletonMap(FunctionRSocketUtils.PAYLOAD, value);
		}
		byte[] data = this.mapper.toJson(value);
		return this.byteArrayEncoder.encodeValue(data, bufferFactory, valueType, mimeType, hints);
	}

	@Override
	public Flux<DataBuffer> encode(Publisher<? extends Object> inputStream,
			DataBufferFactory bufferFactory, ResolvableType elementType,
			MimeType mimeType, Map<String, Object> hints) {
		return Flux.from(inputStream).map(value ->
			encodeValue(value, bufferFactory, elementType, mimeType, hints));
	}
}
