/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.cloud.function.json;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the JSON detection helpers of {@link JsonMapper}, which have a cheap
 * pre-check in front of the actual parsing, give the same answers as the plain
 * parse-based implementation for a wide range of values.
 *
 * @author Oleg Zhurakousky
 */
class JsonMapperDetectionTests {

	private static final ObjectMapper REFERENCE = tools.jackson.databind.json.JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

	private static final List<String> CORPUS = List.of("", " ", "  \n\t ", "{}", "[]", "\"\"", "{\"a\":1}",
			" {\"a\":1} ", "\n[1,2,3]\n", "\t[ ]", "[1,2", "{\"a\":", "{'a':1}", "hello", "Hello World", "test",
			"true", "false", "null", "nul", "TRUE", "123", "-5", "1.5", "1e3", "007", "99999999999", "0x10", "+1",
			"-", "\"text\"", "\"unterminated", "text\"", "{} {}", "[] []", "{}x", "[]]", "1 2", "NaN", "Infinity",
			"// c\n{}", "/* c */[]", "﻿{}", "é", "tr", "fa", "n", "{\"a\":[1,{\"b\":null}]}",
			"[{\"a\":1},{\"a\":2}]", "[\"a\",\"b\"]", "  true  ", "  12  ", "{\"id\":\"1\"}garbage");

	@Test
	void isJsonStringMatchesTheParseBasedImplementation() {
		for (String value : CORPUS) {
			assertThat(JsonMapper.isJsonString(value)).as("String '%s'", value).isEqualTo(reference(value));
			assertThat(JsonMapper.isJsonString(value.getBytes(StandardCharsets.UTF_8)))
					.as("byte[] '%s'", value).isEqualTo(reference(value));
		}
	}

	@Test
	void isJsonStringRepresentsCollectionMatchesTheParseBasedImplementation() {
		for (String value : CORPUS) {
			assertThat(JsonMapper.isJsonStringRepresentsCollection(value)).as("String '%s'", value)
					.isEqualTo(referenceNode(value) instanceof ArrayNode);
			assertThat(JsonMapper.isJsonStringRepresentsCollection(value.getBytes(StandardCharsets.UTF_8)))
					.as("byte[] '%s'", value).isEqualTo(referenceNode(value) instanceof ArrayNode);
		}
	}

	@Test
	void isJsonStringRepresentsMapMatchesTheParseBasedImplementation() {
		for (String value : CORPUS) {
			assertThat(JsonMapper.isJsonStringRepresentsMap(value)).as("String '%s'", value)
					.isEqualTo(referenceNode(value) instanceof ObjectNode);
			assertThat(JsonMapper.isJsonStringRepresentsMap(value.getBytes(StandardCharsets.UTF_8)))
					.as("byte[] '%s'", value).isEqualTo(referenceNode(value) instanceof ObjectNode);
		}
	}

	@Test
	void valuesThatAreNotStringsAreNeverJson() {
		assertThat(JsonMapper.isJsonString(null)).isFalse();
		assertThat(JsonMapper.isJsonString(42)).isFalse();
		assertThat(JsonMapper.isJsonString(new Object())).isFalse();
	}

	/**
	 * The original implementation of {@link JsonMapper#isJsonString(Object)}.
	 */
	private static boolean reference(String value) {
		try {
			REFERENCE.readTree(value);
			try {
				Integer.parseInt(value);
				return false;
			}
			catch (Exception e) {
				return true;
			}
		}
		catch (Exception e) {
			return false;
		}
	}

	private static JsonNode referenceNode(String value) {
		try {
			return REFERENCE.readTree(value);
		}
		catch (Exception e) {
			return null;
		}
	}

}
