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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Maps.newHashMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.function.context.config.NaiveCsvTupleMessageConverter.MAGIC_NULL;
import static org.springframework.cloud.function.context.config.NegotiatingMessageConverterWrapper.ACCEPT;
import static org.springframework.messaging.MessageHeaders.CONTENT_TYPE;

/**
 *
 * @author Florent Biville
 *
 */
public class NegotiatingMessageConverterWrapperTests {

	Collection<Tuple2<?, ?>> somePayload = asList(Tuples.of("hello", "world"), Tuples.of("bonjour", "monde"));

	String expectedSerializedPayload = "hello,world\nbonjour,monde";

	@Test
	public void testSimpleDeserializationDelegation() {
		Message<String> someMessage = MessageBuilder.withPayload("some payload")
			.setHeader(MessageHeaders.CONTENT_TYPE, "text/plain").build();
		AbstractMessageConverter delegate = mock(AbstractMessageConverter.class);

		Object result = NegotiatingMessageConverterWrapper.wrap(delegate).fromMessage(someMessage, String.class);

		verify(delegate).fromMessage(someMessage, String.class);
		assertThat(result).isEqualTo(delegate.fromMessage(someMessage, String.class));
	}

	@Test
	public void testSmartDeserializationDelegation() {
		Message<String> someMessage = MessageBuilder.withPayload("some payload")
			.setHeader(MessageHeaders.CONTENT_TYPE, "text/plain").build();
		MethodParameter someHint = mock(MethodParameter.class);
		AbstractMessageConverter delegate = mock(AbstractMessageConverter.class);

		Object result = NegotiatingMessageConverterWrapper.wrap(delegate)
			.fromMessage(someMessage, String.class, someHint);

		verify(delegate).fromMessage(someMessage, String.class, someHint);
		assertThat(result).isEqualTo(delegate.fromMessage(someMessage, String.class, someHint));
	}

	@Test
	public void testSerializationWithCompatibleConcreteAcceptHeader() {
		MimeType acceptableType = MimeType.valueOf("text/csv");

		Message<?> result = NegotiatingMessageConverterWrapper.wrap(new NaiveCsvTupleMessageConverter())
			.toMessage(somePayload, new MessageHeaders(newHashMap(ACCEPT, acceptableType)));

		assertMessageContent(result, "text/csv", expectedSerializedPayload);
	}

	@Test
	public void testSerializationWithCompatibleConcreteAcceptHeaderAndExtraHeaders() {
		MimeType acceptableType = MimeType.valueOf("text/csv");
		Map<String, Object> headers = new HashMap<>(2, 1f);
		headers.put(ACCEPT, acceptableType);
		headers.put("extra", "ordinary");

		Message<?> result = NegotiatingMessageConverterWrapper.wrap(new NaiveCsvTupleMessageConverter())
			.toMessage(somePayload, new MessageHeaders(headers));

		assertMessageContent(result, "text/csv", expectedSerializedPayload);
		assertThat(result.getHeaders()).containsEntry("extra", "ordinary");
	}

	@Test
	public void testSerializationWithCompatibleWildcardSubtypeAcceptHeader() {
		MimeType acceptableType = MimeType.valueOf("text/*");

		Message<?> result = NegotiatingMessageConverterWrapper.wrap(new NaiveCsvTupleMessageConverter())
			.toMessage(somePayload, new MessageHeaders(newHashMap(ACCEPT, acceptableType)));

		assertMessageContent(result, "text/csv", expectedSerializedPayload);
	}

	@Test
	public void testSerializationWithCompatibleWildcardAcceptHeader() {
		MimeType acceptableType = MimeType.valueOf("*/*");

		Message<?> result = NegotiatingMessageConverterWrapper.wrap(new NaiveCsvTupleMessageConverter())
			.toMessage(somePayload, new MessageHeaders(newHashMap(ACCEPT, acceptableType)));

		assertMessageContent(result, "text/csv", expectedSerializedPayload);
	}

	@Test
	public void testSerializationWithFallbackContentTypeHeader() {
		MimeType fallbackContentType = MimeType.valueOf("text/csv");

		Message<?> result = NegotiatingMessageConverterWrapper.wrap(new NaiveCsvTupleMessageConverter())
			.toMessage(somePayload, new MessageHeaders(newHashMap(CONTENT_TYPE, fallbackContentType)));

		assertMessageContent(result, "text/csv", expectedSerializedPayload);
	}

	@Test
	public void testNoSerializationWithoutMimeType() {
		Message<?> result = NegotiatingMessageConverterWrapper.wrap(new NaiveCsvTupleMessageConverter())
			.toMessage(somePayload, new MessageHeaders(null));

		assertThat(result).overridingErrorMessage("Serialization should not happen").isNull();
	}

	@Test
	public void testNoSerializationWithIncompatibleAcceptHeader() {
		MimeType acceptableType = MimeType.valueOf("application/*");

		Message<?> result = NegotiatingMessageConverterWrapper.wrap(new NaiveCsvTupleMessageConverter())
			.toMessage(somePayload, new MessageHeaders(newHashMap(ACCEPT, acceptableType)));

		assertThat(result).overridingErrorMessage("Serialization should not happen").isNull();
	}

	@Test
	public void testNoSerializationWithIncompatibleFallbackContentTypeHeader() {
		MimeType fallbackContentType = MimeType.valueOf("application/*");

		Message<?> result = NegotiatingMessageConverterWrapper.wrap(new NaiveCsvTupleMessageConverter())
			.toMessage(somePayload, new MessageHeaders(newHashMap(CONTENT_TYPE, fallbackContentType)));

		assertThat(result).overridingErrorMessage("Serialization should not happen").isNull();
	}

	@Test
	public void testNoSerializationWithNullPayload() {
		Object payload = MAGIC_NULL;
		MimeType acceptableType = MimeType.valueOf("text/csv");

		Message<?> result = NegotiatingMessageConverterWrapper.wrap(new NaiveCsvTupleMessageConverter())
			.toMessage(payload, new MessageHeaders(newHashMap(ACCEPT, acceptableType)));

		assertThat(result).overridingErrorMessage("Serialization should not happen").isNull();
	}

	private void assertMessageContent(Message<?> result, String expectedContentType, String payload) {
		assertThat(result)
			.overridingErrorMessage("serialization should have succeeded")
			.isNotNull();
		assertThat(result.getPayload()).isEqualTo(payload);
		assertThat(result.getHeaders())
			.doesNotContainKey(ACCEPT)
			.containsEntry(CONTENT_TYPE, MimeType.valueOf(expectedContentType));
	}
}

class NaiveCsvTupleMessageConverter extends AbstractMessageConverter {

	public static final Collection<Tuple2<?, ?>> MAGIC_NULL = Collections.emptyList();

	NaiveCsvTupleMessageConverter() {
		super(singletonList(MimeType.valueOf("text/csv")));
	}

	@Override
	public Object convertToInternal(Object rawPayload, MessageHeaders headers, Object conversionHint) {
		if (rawPayload == MAGIC_NULL) {
			return null;
		}
		return ((Collection<Tuple2<?, ?>>) rawPayload)
			.stream()
			.map(tuple -> String.format("%s,%s", tuple.getT1(), tuple.getT2()))
			.collect(Collectors.joining("\n"));
	}


	@Override
	protected boolean supports(Class<?> clazz) {
		return Collection.class.isAssignableFrom(clazz);
	}
}
