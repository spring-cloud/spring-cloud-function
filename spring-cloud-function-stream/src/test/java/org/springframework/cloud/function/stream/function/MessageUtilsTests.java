/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.stream.function;

import java.util.Collections;
import java.util.function.Function;

import org.junit.Test;

import org.springframework.beans.BeanUtils;
import org.springframework.cloud.function.context.message.MessageUtils;
import org.springframework.cloud.function.core.IsolatedFunction;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class MessageUtilsTests {

	private ClassLoader loader = ClassLoaderUtils.createClassLoader();

	@Test
	public void testCreateNotIsolated() throws Exception {
		Object function = new Uppercase();
		Object output = MessageUtils.create(function, "foo", Collections.emptyMap());
		assertThat(output).isInstanceOf(Message.class);
	}

	@Test
	public void testUnpackNotIsolated() throws Exception {
		Object function = new Uppercase();
		Object output = MessageUtils.unpack(function,
				MessageBuilder.withPayload("foo").build());
		assertThat(output).isInstanceOf(Message.class);
	}

	@Test
	public void testUnpackNotIsolatedNotMessage() throws Exception {
		Object function = new Uppercase();
		Object output = MessageUtils.unpack(function, "foo");
		assertThat(output).isInstanceOf(Message.class);
	}

	@Test
	public void testUnpackIsolated() throws Exception {
		Object function = create(Uppercase.class);
		Object output = MessageUtils.unpack(function, message(function, "foo"));
		assertThat(output).isInstanceOf(Message.class);
	}

	@Test
	public void testUnpackIsolatedNotMessage() throws Exception {
		Object function = create(Uppercase.class);
		Object output = MessageUtils.unpack(function, "foo");
		assertThat(output).isInstanceOf(Message.class);
		@SuppressWarnings("unchecked")
		Message<String> message = (Message<String>) output;
		assertThat(message.getPayload()).isEqualTo("foo");
	}

	@Test
	public void testUnpackIsolatedMessageNotAvailable() throws Exception {
		Object function = create(Uppercase.class,
				ClassLoaderUtils.createMinimalClassLoader());
		Object output = MessageUtils.unpack(function, "foo");
		assertThat(output).isInstanceOf(Message.class);
		@SuppressWarnings("unchecked")
		Message<String> message = (Message<String>) output;
		assertThat(message.getPayload()).isEqualTo("foo");
	}

	@Test
	public void testCreateIsolated() throws Exception {
		Object function = create(Uppercase.class);
		Object output = MessageUtils.create(function, "foo", Collections.emptyMap());
		assertThat(output).isNotInstanceOf(Message.class);
	}

	private Object message(Object function, Object payload) {
		return MessageUtils.create(function, payload, Collections.emptyMap());
	}

	private Object create(Class<Uppercase> type) {
		return create(type, loader);
	}

	private Object create(Class<Uppercase> type, ClassLoader loader) {
		return new IsolatedFunction<>((Function<?, ?>) BeanUtils
				.instantiate(ClassUtils.resolveClassName(type.getName(), loader)));
	}

	public static class Uppercase implements Function<Message<String>, Message<String>> {
		@Override
		public Message<String> apply(Message<String> message) {
			return MessageBuilder.withPayload(message.getPayload().toUpperCase())
					.copyHeadersIfAbsent(message.getHeaders()).build();
		}
	}
}
