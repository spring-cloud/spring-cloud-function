/*
 * Copyright 2012-present the original author or authors.
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

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.function.json.GsonMapper;
import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.util.ReflectionUtils;

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

	@Test
	public void objectNode_isJsonStringRepresentsCollection() {
		ObjectNode node = JsonNodeFactory.instance.objectNode();
		node.put("id", "1234ab");
		node.put("foo", "bar");

		/*
		 * Passing the ObjectNode directly results in a positive identification as
		 * a collection, as its distant parent JsonNode implements Iterable.
		 */
		assertThat(JsonMapper.isJsonStringRepresentsCollection(node)).isFalse();

		String nodeAsString = node.toString();

		/*
		 * Sending the node as a string returns false, however, as the line
		 * isJsonString(value) && str.startsWith("[") && str.endsWith("]")
		 * will not be true.
		 */
		assertThat(JsonMapper.isJsonStringRepresentsCollection(nodeAsString)).isFalse();
	}

	// see https://github.com/spring-cloud/spring-cloud-function/issues/1189
	@Test
	@Disabled("https://github.com/spring-cloud/spring-cloud-function/issues/1304")
	public void testJsonDateTimeConversion() {
		ApplicationContext context = SpringApplication.run(EmptyConfiguration.class);
		JsonMapper jsonMapper = context.getBean(JsonMapper.class);
		StringVsTimestamp dom = new StringVsTimestamp("2024-10-16T16:13:29.964361+02:00");
		String convertedJson = new String(jsonMapper.toJson(dom), StandardCharsets.UTF_8);
		assertThat(convertedJson).contains("\"zonedDateTime\":\"2024-10-16T16:13:29.964361+02:00\"");
	}

	@Test
	public void testKotlinModuleRegistration() throws Exception {
		ApplicationContext context = SpringApplication.run(EmptyConfiguration.class);
		JsonMapper jsonMapper = context.getBean(JsonMapper.class);
		Field mapperField = ReflectionUtils.findField(jsonMapper.getClass(), "mapper");
		mapperField.setAccessible(true);
		ObjectMapper mapper = (ObjectMapper) mapperField.get(jsonMapper);
		assertThat(mapper.getRegisteredModuleIds()).contains("com.fasterxml.jackson.module.kotlin.KotlinModule");
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

	@EnableAutoConfiguration
	@Configuration
	static class EmptyConfiguration {

	}

	static class StringVsTimestamp {

		private String type;

		private Date date;

		private ZonedDateTime zonedDateTime;

		StringVsTimestamp(String zonedDate) {
			type = "StringVsTimestamp";
			date = new Date();
			zonedDateTime = ZonedDateTime.parse(zonedDate);
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public Date getDate() {
			return date;
		}

		public void setDate(Date date) {
			this.date = date;
		}

		public ZonedDateTime getZonedDateTime() {
			return zonedDateTime;
		}

		public void setZonedDateTime(ZonedDateTime zonedDateTime) {
			this.zonedDateTime = zonedDateTime;
		}
	}
}
