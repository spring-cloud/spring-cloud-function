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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDecoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.lang.Nullable;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 *
 */
class MessageAwareJsonDecoder extends AbstractDecoder<Object> {

	private final JsonMapper jsonMapper;

	MessageAwareJsonDecoder(JsonMapper jsonMapper) {
		super(MimeTypeUtils.APPLICATION_JSON);
		this.jsonMapper = jsonMapper;
	}

	@Override
	public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
		return mimeType != null && mimeType.isCompatibleWith(MimeTypeUtils.APPLICATION_JSON);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Object decode(DataBuffer dataBuffer, ResolvableType targetType,
			@Nullable MimeType mimeType, @Nullable Map<String, Object> hints)
			throws DecodingException {

		ResolvableType type = ResolvableType.forClassWithGenerics(Map.class, String.class,
				Object.class);
		Map<String, Object> messageMap = (Map<String, Object>) doDecode(dataBuffer, type,
				mimeType, hints);
		if (messageMap.containsKey(FunctionRSocketUtils.PAYLOAD)) {
			Type requestedType = FunctionTypeUtils.getGenericType(targetType.getType());
			Object payload;
			if (String.class.isAssignableFrom(FunctionTypeUtils.getRawType(targetType.getType()))) {
				Object rawPayload = messageMap.get(FunctionRSocketUtils.PAYLOAD);
				if (rawPayload instanceof byte[]) {
					payload = new String((byte[]) rawPayload, StandardCharsets.UTF_8);
				}
				else {
					payload = rawPayload;
				}
			}
			else if (byte[].class.isAssignableFrom(FunctionTypeUtils.getRawType(targetType.getType())))  {
				Object rawPayload = messageMap.get(FunctionRSocketUtils.PAYLOAD);
				if (rawPayload instanceof String) {
					payload = ((String) rawPayload).getBytes(StandardCharsets.UTF_8);
				}
				else {
					payload = rawPayload;
				}
			}
			else {
				payload =  this.jsonMapper.fromJson(messageMap.get(FunctionRSocketUtils.PAYLOAD), requestedType);
			}
//			if (String.class.isAssignableFrom(FunctionTypeUtils.getRawType(targetType.getType()))
//					|| byte[].class.isAssignableFrom(FunctionTypeUtils.getRawType(targetType.getType()))) {
//				Object rawPayload = messageMap.get(FunctionRSocketUtils.PAYLOAD);
//				if (rawPayload instanceof byte[]) {
//					payload = new String((byte[]) rawPayload, StandardCharsets.UTF_8);
//				}
//				else {
//					payload = rawPayload;
//				}
//			}
//			else {
//				payload =  this.jsonMapper.fromJson(messageMap.get(FunctionRSocketUtils.PAYLOAD), requestedType);
//			}

			if (FunctionTypeUtils.isMessage(targetType.getType())) {
				return MessageBuilder.withPayload(payload).copyHeaders(
						(Map<String, ?>) messageMap.get(FunctionRSocketUtils.HEADERS))
						.build();
			}
			else {
				return payload;
			}
		}
		else {
			return messageMap;
		}
	}

	private Object doDecode(DataBuffer dataBuffer, ResolvableType targetType,
			@Nullable MimeType mimeType, @Nullable Map<String, Object> hints)
			throws DecodingException {

		try {
			byte[] data = toByteArray(dataBuffer.asInputStream());
			if (JsonMapper.isJsonStringRepresentsMap(data)) {
				return this.jsonMapper.fromJson(data, targetType.getType());
			}
			else {
				Map<String, Object> messageMap = new HashMap<>();
				messageMap.put(FunctionRSocketUtils.PAYLOAD, data);
				return messageMap;
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		finally {
			DataBufferUtils.release(dataBuffer);
		}
	}

	private byte[] toByteArray(final InputStream input) throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			copyLarge(input, output, new byte[2048]);
			return output.toByteArray();
		}
	}

	private long copyLarge(final InputStream input, final OutputStream output,
			final byte[] buffer) throws IOException {
		long count = 0;
		int n;
		while (-1 != (n = input.read(buffer))) {
			output.write(buffer, 0, n);
			count += n;
		}
		return count;
	}

	@Override
	public Flux<Object> decode(Publisher<DataBuffer> inputStream,
			ResolvableType elementType, MimeType mimeType, Map<String, Object> hints) {
		return Flux.from(inputStream).map(buffer -> decode(buffer, elementType, mimeType, hints));
	}
}
