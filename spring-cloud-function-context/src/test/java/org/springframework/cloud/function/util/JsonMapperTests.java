/*
 * Copyright 2016-2018 the original author or authors.
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
package org.springframework.cloud.function.util;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import org.springframework.cloud.function.json.GsonMapper;
import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.core.ResolvableType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 *
 */
@RunWith(Parameterized.class)
public class JsonMapperTests {

	private JsonMapper mapper;

	@Parameters
	public static List<Object[]> params() {
		return Arrays.asList(new Object[] { new GsonMapper(new Gson()) },
				new Object[] { new JacksonMapper(new ObjectMapper()) });
	}

	public JsonMapperTests(JsonMapper mapper) {
		this.mapper = mapper;
	}

	@Test
	public void vanillaArray() {
		String json = "[{\"value\":\"foo\"},{\"value\":\"foo\"}]";
		List<Foo> list = mapper.toObject(json,
				ResolvableType.forClassWithGenerics(List.class, Foo.class).getType());
		assertThat(list).hasSize(2);
		assertThat(list.get(0).getValue()).isEqualTo("foo");
		assertThat(mapper.toString(list)).isEqualTo(json);
	}

	@Test
	public void intArray() {
		List<Integer> list = mapper.toObject("[123,456]",
				ResolvableType.forClassWithGenerics(List.class, Integer.class).getType());
		assertThat(list).hasSize(2);
		assertThat(list.get(0)).isEqualTo(123);
	}

	@Test
	public void emptyArray() {
		List<Foo> list = mapper.toObject("[]",
				ResolvableType.forClassWithGenerics(List.class, Foo.class).getType());
		assertThat(list).hasSize(0);
	}

	@Test
	public void vanillaObject() {
		String json = "{\"value\":\"foo\"}";
		Foo foo = mapper.toObject(json, Foo.class);
		assertThat(foo.getValue()).isEqualTo("foo");
		assertThat(mapper.toString(foo)).isEqualTo(json);
	}

	@Test
	public void intValue() {
		int foo = mapper.toObject("123", Integer.class);
		assertThat(foo).isEqualTo(123);
	}

	@Test
	public void empty() {
		Foo foo = mapper.toObject("{}", Foo.class);
		assertThat(foo.getValue()).isNull();
	}

	public static class Foo {
		private String value;

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}

	}
}
