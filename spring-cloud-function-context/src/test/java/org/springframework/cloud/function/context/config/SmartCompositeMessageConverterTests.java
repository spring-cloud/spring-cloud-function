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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SmartCompositeMessageConverter} guarding against
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
