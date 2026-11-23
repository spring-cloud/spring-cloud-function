/*
 * Copyright 2015-present the original author or authors.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link SmartCompositeMessageConverter}, including guarding against
 * {@link java.util.ConcurrentModificationException} (see GH-1442) when the underlying
 * converters list is structurally modified while a conversion is being performed.
 */
class SmartCompositeMessageConverterTests {

	@Test
	void toMessageSnapshotsConvertersWhenListMutatedDuringIteration() {
		SmartCompositeMessageConverter composite = new SmartCompositeMessageConverter(
				List.of(new NoOpMessageConverter()));
		composite.getConverters().add(new MutatingMessageConverter(composite));
		MessageHeaders headers = new MessageHeaders(
				Map.of(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON));

		assertThat(composite.toMessage("payload", headers)).isNull();
	}

	@Test
	void fromMessageSnapshotsConvertersWhenListMutatedDuringIteration() {
		SmartCompositeMessageConverter composite = new SmartCompositeMessageConverter(
				List.of(new NoOpMessageConverter()));
		composite.getConverters().add(new MutatingMessageConverter(composite));
		Message<String> message = MessageBuilder.withPayload("hello").build();

		assertThat(composite.fromMessage(message, Integer.class)).isNull();
	}

	@Test
	void toMessageWithCrossThreadListMutationDuringIterationIsSafe() throws Exception {
		SmartCompositeMessageConverter composite = new SmartCompositeMessageConverter(
				List.of(new NoOpMessageConverter()));
		CountDownLatch conversionEntered = new CountDownLatch(1);
		CountDownLatch releaseConversion = new CountDownLatch(1);
		composite.getConverters().add(new BlockingMessageConverter(conversionEntered, releaseConversion));
		MessageHeaders headers = new MessageHeaders(
				Map.of(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON));
		AtomicReference<Throwable> failure = new AtomicReference<>();

		Thread reader = new Thread(() -> {
			try {
				composite.toMessage("payload", headers);
			}
			catch (Throwable t) {
				failure.set(t);
			}
		});
		reader.start();
		try {
			// Wait until the reader is suspended inside the converters iteration,
			// then structurally modify the live list from this thread.
			assertThat(conversionEntered.await(5, TimeUnit.SECONDS)).isTrue();
			composite.getConverters().add(new NoOpMessageConverter());
		}
		finally {
			releaseConversion.countDown();
		}
		reader.join(5000);

		assertThat(reader.isAlive()).isFalse();
		assertThat(failure.get()).isNull();
	}

	@Test
	void toMessageWithoutHeadersReturnsNull() {
		SmartCompositeMessageConverter composite = new SmartCompositeMessageConverter(
				List.of(new UpperCaseMessageConverter()));

		assertThat(composite.toMessage("payload", null)).isNull();
		assertThat(composite.toMessage("payload", null, String.class)).isNull();
	}

	@Test
	void toMessageWithConversionHintAndWithoutContentTypeReturnsNull() {
		SmartCompositeMessageConverter composite = new SmartCompositeMessageConverter(
				List.of(new UpperCaseMessageConverter()));

		assertThat(composite.toMessage("payload", new MessageHeaders(Map.of()), String.class)).isNull();
	}

	@Test
	void toMessageWithConversionHintSupportsPlainMessageConverter() {
		SmartCompositeMessageConverter composite = new SmartCompositeMessageConverter(
				List.of(new UpperCaseMessageConverter()));
		MessageHeaders headers = new MessageHeaders(
				Map.of(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.TEXT_PLAIN));

		Message<?> result = composite.toMessage("payload", headers, String.class);

		assertThat(result).isNotNull();
		assertThat(result.getPayload()).isEqualTo("PAYLOAD");
	}

	@Test
	void toMessageWithConversionHintAndWildcardContentTypeSkipsPlainMessageConverter() {
		SmartCompositeMessageConverter composite = new SmartCompositeMessageConverter(
				List.of(new UpperCaseMessageConverter()));
		MessageHeaders headers = new MessageHeaders(
				Map.of(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.ALL));

		assertThat(composite.toMessage("payload", headers, String.class)).isNull();
	}

	@Test
	void fromMessageWithConversionHintTriesNextConverterWhenOneFails() {
		SmartCompositeMessageConverter composite = new SmartCompositeMessageConverter(
				List.of(new FailingMessageConverter(), new UpperCaseMessageConverter()));
		Message<String> message = MessageBuilder.withPayload("hello").build();

		assertThat(composite.fromMessage(message, Integer.class, Integer.class)).isEqualTo("HELLO");
	}

	@Test
	void fromMessageWithConversionHintConvertsBatchWhenOneConverterFails() {
		SmartCompositeMessageConverter composite = new SmartCompositeMessageConverter(
				List.of(new FailingMessageConverter(), new UpperCaseMessageConverter()));
		Message<List<String>> message = MessageBuilder.withPayload(List.of("a", "b")).build();

		Object result = composite.fromMessage(message, List.class,
				new ParameterizedTypeReference<List<Integer>>() { }.getType());

		assertThat(result).isEqualTo(List.of("A", "B"));
	}

	@Test
	void fromMessageWithConversionHintKeepsNullBatchElements() {
		SmartCompositeMessageConverter composite = new SmartCompositeMessageConverter(
				List.of(new UpperCaseMessageConverter()));
		Message<List<String>> message = MessageBuilder.withPayload(Arrays.asList("a", null, "b")).build();

		Object result = composite.fromMessage(message, List.class,
				new ParameterizedTypeReference<List<Integer>>() { }.getType());

		assertThat(result).isEqualTo(Arrays.asList("A", null, "B"));
	}

	@Test
	void conversionFailureDoesNotExposePayload() {
		MessageConverterHelper failingHelper = new MessageConverterHelper() {
			@Override
			public boolean shouldFailIfCantConvert(Message<?> message) {
				return true;
			}
		};
		SmartCompositeMessageConverter composite = new SmartCompositeMessageConverter(
				List.of(new NoOpMessageConverter()), () -> Collections.singletonList(failingHelper));
		Message<String> message = MessageBuilder.withPayload("secret-payload").build();

		assertThatExceptionOfType(MessageConversionException.class)
				.isThrownBy(() -> composite.fromMessage(message, Integer.class, Integer.class))
				.withMessageNotContaining("secret-payload")
				.withMessageContaining(String.valueOf(message.getHeaders().getId()));
	}

	@NullMarked
	private record MutatingMessageConverter(SmartCompositeMessageConverter composite) implements MessageConverter {

		@Override
		@Nullable
		public Object fromMessage(Message<?> message, Class<?> targetClass) {
			this.composite().getConverters().add(new MutatingMessageConverter(this.composite()));
			return null;
		}

		@Override
		@Nullable
		public Message<?> toMessage(Object payload, @Nullable MessageHeaders headers) {
			this.composite().getConverters().add(new MutatingMessageConverter(this.composite()));
			return null;
		}

	}

	@NullMarked
	private record BlockingMessageConverter(CountDownLatch conversionEntered, CountDownLatch releaseConversion)
			implements MessageConverter {

		@Override
		@Nullable
		public Object fromMessage(Message<?> message, Class<?> targetClass) {
			return null;
		}

		@Override
		@Nullable
		public Message<?> toMessage(Object payload, @Nullable MessageHeaders headers) {
			this.conversionEntered().countDown();
			try {
				this.releaseConversion().await();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return null;
		}

	}

	/**
	 * A converter that implements neither {@code SmartMessageConverter} nor extends
	 * {@code AbstractMessageConverter}, as a user-provided converter might.
	 */
	@NullMarked
	private static final class UpperCaseMessageConverter implements MessageConverter {

		@Override
		@Nullable
		public Object fromMessage(Message<?> message, Class<?> targetClass) {
			return (message.getPayload() instanceof String payload) ? payload.toUpperCase(Locale.ROOT) : null;
		}

		@Override
		@Nullable
		public Message<?> toMessage(Object payload, @Nullable MessageHeaders headers) {
			return (payload instanceof String value) ? MessageBuilder.withPayload(value.toUpperCase(Locale.ROOT)).build() : null;
		}

	}

	@NullMarked
	private static final class FailingMessageConverter implements MessageConverter {

		@Override
		@Nullable
		public Object fromMessage(Message<?> message, Class<?> targetClass) {
			throw new IllegalStateException("Simulated conversion failure");
		}

		@Override
		@Nullable
		public Message<?> toMessage(Object payload, @Nullable MessageHeaders headers) {
			throw new IllegalStateException("Simulated conversion failure");
		}

	}

	@NullMarked
	private static final class NoOpMessageConverter implements MessageConverter {

		@Override
		@Nullable
		public Object fromMessage(Message<?> message, Class<?> targetClass) {
			return null;
		}

		@Override
		@Nullable
		public Message<?> toMessage(Object payload, @Nullable MessageHeaders headers) {
			return null;
		}

	}

}
