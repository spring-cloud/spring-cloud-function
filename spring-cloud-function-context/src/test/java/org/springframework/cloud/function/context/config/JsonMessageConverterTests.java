/*
 * Copyright 2023-present the original author or authors.
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

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;


import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class JsonMessageConverterTests {

	@Test
	public void testTypeInference() {
		JsonMessageConverter converter = new JsonMessageConverter(new JacksonMapper(new ObjectMapper()));

		Message<String> message = MessageBuilder.withPayload("{\"name\":\"bill\"}").build();
		assertThat(converter.canConvertFrom(message, Person.class)).isTrue();
		assertThat(converter.canConvertFrom(message, Object.class)).isFalse();
		assertThat(converter.canConvertFrom(message, null)).isFalse();

		message = MessageBuilder.withPayload("{\"name\":\"bill\"}").setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON).build();
		assertThat(converter.canConvertFrom(message, Person.class)).isTrue();
		assertThat(converter.canConvertFrom(message, Object.class)).isFalse();
		assertThat(converter.canConvertFrom(message, null)).isFalse();
		assertThat(converter.convertFromInternal(message, Person.class, null)).isInstanceOf(Person.class);

		message = MessageBuilder.withPayload("{\"name\":\"bill\"}")
				.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON.toString() + ";type=" + Person.class.getName()).build();
		assertThat(converter.canConvertFrom(message, Object.class)).isTrue();
		assertThat(converter.canConvertFrom(message, null)).isTrue();
		assertThat(converter.convertFromInternal(message, Person.class, null)).isInstanceOf(Person.class);
		assertThat(converter.convertFromInternal(message, Object.class, null)).isInstanceOf(Person.class);
		assertThat(converter.convertFromInternal(message, null, null)).isInstanceOf(Person.class);
	}

	public static class Person {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
