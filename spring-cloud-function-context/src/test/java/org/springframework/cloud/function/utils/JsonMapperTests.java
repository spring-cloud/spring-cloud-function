/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.cloud.function.utils;

import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
public class JsonMapperTests {

	public static Stream<JsonMapper> params() {
		return Stream.of(new GsonMapper(new Gson()), new JacksonMapper(new ObjectMapper()));
	}

	@ParameterizedTest
	@MethodSource("params")
	public void vanillaArray(JsonMapper mapper) {
		String json = "[{\"value\":\"foo\"},{\"value\":\"foo\"}]";
		List<Foo> list = mapper.fromJson(json,
				ResolvableType.forClassWithGenerics(List.class, Foo.class).getType());
		assertThat(list).hasSize(2);
		assertThat(list.get(0).getValue()).isEqualTo("foo");
		assertThat(mapper.toString(list)).isEqualTo(json);
	}

	@ParameterizedTest
	@MethodSource("params")
	public void intArray(JsonMapper mapper) {
		List<Integer> list = mapper.fromJson("[123,456]",
				ResolvableType.forClassWithGenerics(List.class, Integer.class).getType());
		assertThat(list).hasSize(2);
		assertThat(list.get(0)).isEqualTo(123);
	}

	@ParameterizedTest
	@MethodSource("params")
	public void emptyArray(JsonMapper mapper) {
		List<Foo> list = mapper.fromJson("[]",
				ResolvableType.forClassWithGenerics(List.class, Foo.class).getType());
		assertThat(list).hasSize(0);
	}

	@ParameterizedTest
	@MethodSource("params")
	public void vanillaObject(JsonMapper mapper) {
		String json = "{\"value\":\"foo\"}";
		Foo foo = mapper.fromJson(json, Foo.class);
		assertThat(foo.getValue()).isEqualTo("foo");
		assertThat(mapper.toString(foo)).isEqualTo(json);
	}

	@ParameterizedTest
	@MethodSource("params")
	public void stringRepresentingJson(JsonMapper mapper) {
		String json = "{\"value\":\"foo\"}";
		byte[] bytes = mapper.toJson(json);
		assertThat(new String(bytes)).isEqualTo(json);
	}

	@ParameterizedTest
	@MethodSource("params")
	public void intValue(JsonMapper mapper) {
		int foo = mapper.fromJson("123", Integer.class);
		assertThat(foo).isEqualTo(123);
	}

	@ParameterizedTest
	@MethodSource("params")
	public void empty(JsonMapper mapper) {
		Foo foo = mapper.fromJson("{}", Foo.class);
		assertThat(foo.getValue()).isNull();
	}

	public static class Foo {

		private String value;

		public String getValue() {
			return this.value;
		}

		public void setValue(String value) {
			this.value = value;
		}

	}

}
