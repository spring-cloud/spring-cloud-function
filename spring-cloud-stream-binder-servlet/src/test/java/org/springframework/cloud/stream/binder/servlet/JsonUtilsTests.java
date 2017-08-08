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
package org.springframework.cloud.stream.binder.servlet;

import java.util.Arrays;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class JsonUtilsTests {

	private ObjectMapper mapper = new ObjectMapper();

	@Test
	public void empty() {
		assertThat(JsonUtils.split("[]")).isEmpty();
	}

	@Test
	public void strings() {
		assertThat(JsonUtils.split("[\"foo\", \"bar\"]")).hasSize(2).contains("foo",
				"bar");
	}

	@Test
	public void objects() throws Exception {
		assertThat(JsonUtils.split(
				mapper.writeValueAsString(Arrays.asList(new Foo("foo"), new Foo("bar")))))
						.hasSize(2)
						.contains("{\"value\":\"foo\"}", "{\"value\":\"bar\"}");
	}

	@Test
	public void arrays() throws Exception {
		assertThat(JsonUtils.split(mapper.writeValueAsString(
				Arrays.asList(Arrays.asList(new Foo("foo"), new Foo("bar")))))).hasSize(1)
						.contains("[{\"value\":\"foo\"},{\"value\":\"bar\"}]");
	}

	protected static class Foo {
		private String value;

		public Foo() {
		}

		public Foo(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}

	}
}
