/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.web;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

import org.reactivestreams.Publisher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.StringDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.util.MimeType;
import org.springframework.web.reactive.config.WebReactiveConfigurer;

import reactor.core.publisher.Flux;

/**
 * @author Mark Fisher
 */
@SpringBootApplication
public class RestApplication implements WebReactiveConfigurer {

	@Override
	public void extendMessageReaders(List<HttpMessageReader<?>> readers) {
		readers.add(0, new DecoderHttpMessageReader<>(new SseDecoder()));
	}

	public static void main(String[] args) {
		SpringApplication.run(RestApplication.class, args);
	}
}

class SseDecoder extends StringDecoder {

	private static final IntPredicate NEWLINE_DELIMITER = b -> b == '\n' || b == '\r';

	@Override
	public boolean canDecode(ResolvableType elementType, MimeType mimeType) {
		return super.canDecode(elementType, mimeType)
				&& MediaType.TEXT_EVENT_STREAM.isCompatibleWith(mimeType);
	}

	@Override
	public Flux<String> decode(Publisher<DataBuffer> inputStream,
			ResolvableType elementType, MimeType mimeType, Map<String, Object> hints) {

		Flux<DataBuffer> inputFlux = Flux.from(inputStream);
		inputFlux = Flux.from(inputStream).flatMap(SseDecoder::splitOnNewline);
		return inputFlux.map(buffer -> decodeDataBuffer(buffer, mimeType));
	}

	private static Flux<DataBuffer> splitOnNewline(DataBuffer dataBuffer) {
		List<DataBuffer> results = new ArrayList<>();
		int startIdx = 0;
		int endIdx;
		final int limit = dataBuffer.readableByteCount();
		do {
			endIdx = dataBuffer.indexOf(NEWLINE_DELIMITER, startIdx);
			endIdx = dataBuffer.indexOf(NEWLINE_DELIMITER, endIdx + 1);
			int length = (endIdx != -1 ? endIdx - startIdx + 1 : limit - startIdx) - 7;
			if (length > 0) {
				DataBuffer token = dataBuffer.slice(startIdx + 5, length);
				results.add(DataBufferUtils.retain(token));
			}
			startIdx = endIdx + 1;
		}
		while (startIdx < limit && endIdx != -1);
		DataBufferUtils.release(dataBuffer);
		return Flux.fromIterable(results);
	}

	private String decodeDataBuffer(DataBuffer dataBuffer, MimeType mimeType) {
		Charset charset = getCharset(mimeType);
		CharBuffer charBuffer = charset.decode(dataBuffer.asByteBuffer());
		DataBufferUtils.release(dataBuffer);
		return charBuffer.toString();
	}

	private Charset getCharset(MimeType mimeType) {
		if (mimeType != null && mimeType.getCharset() != null) {
			return mimeType.getCharset();
		}
		else {
			return DEFAULT_CHARSET;
		}
	}
}