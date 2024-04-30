/*
 * Copyright 2024-2024 the original author or authors.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;


public class JsonMaskerTests {

	private String event = "{\n"
			+ "  \"Records\": [\n"
			+ "    {\n"
			+ "      \"eventID\": \"f07f8ca4b0b26cb9c4e5e77e69f274ee\",\n"
			+ "      \"eventName\": \"INSERT\",\n"
			+ "      \"eventVersion\": \"1.1\",\n"
			+ "      \"eventSource\": \"aws:dynamodb\",\n"
			+ "      \"awsRegion\": \"us-east-1\",\n"
			+ "      \"userIdentity\":{\n"
			+ "        \"type\":\"Service\",\n"
			+ "        \"principalId\":\"dynamodb.amazonaws.com\"\n"
			+ "      },\n"
			+ "      \"dynamodb\": {\n"
			+ "        \"ApproximateCreationDateTime\": 1.684934517E9,\n"
			+ "        \"Keys\": {\n"
			+ "          \"val\": {\n"
			+ "            \"S\": \"data\"\n"
			+ "          },\n"
			+ "          \"key\": {\n"
			+ "            \"S\": \"binary\"\n"
			+ "          }\n"
			+ "        },\n"
			+ "        \"NewImage\": {\n"
			+ "          \"val\": {\n"
			+ "            \"S\": \"data\"\n"
			+ "          },\n"
			+ "          \"asdf1\": {\n"
			+ "            \"B\": \"AAEqQQ==\"\n"
			+ "          },\n"
			+ "          \"asdf2\": {\n"
			+ "            \"BS\": [\n"
			+ "              \"AAEqQQ==\",\n"
			+ "              \"QSoBAA==\"\n"
			+ "            ]\n"
			+ "          },\n"
			+ "          \"key\": {\n"
			+ "            \"S\": \"binary\"\n"
			+ "          }\n"
			+ "        },\n"
			+ "        \"SequenceNumber\": \"1405400000000002063282832\",\n"
			+ "        \"SizeBytes\": 54,\n"
			+ "        \"StreamViewType\": \"NEW_AND_OLD_IMAGES\"\n"
			+ "      },\n"
			+ "      \"eventSourceARN\": \"arn:aws:dynamodb:us-east-1:123456789012:table/Example-Table/stream/2016-12-01T00:00:00.000\"\n"
			+ "    },\n"
			+ "    {\n"
			+ "      \"eventID\": \"f07f8ca4b0b26cb9c4e5e77e42f274ee\",\n"
			+ "      \"eventName\": \"INSERT\",\n"
			+ "      \"eventVersion\": \"1.1\",\n"
			+ "      \"eventSource\": \"aws:dynamodb\",\n"
			+ "      \"awsRegion\": \"us-east-1\",\n"
			+ "      \"dynamodb\": {\n"
			+ "        \"ApproximateCreationDateTime\": 1480642020,\n"
			+ "        \"Keys\": {\n"
			+ "          \"val\": {\n"
			+ "            \"S\": \"data\"\n"
			+ "          },\n"
			+ "          \"key\": {\n"
			+ "            \"S\": \"binary\"\n"
			+ "          }\n"
			+ "        },\n"
			+ "        \"NewImage\": {\n"
			+ "          \"val\": {\n"
			+ "            \"S\": \"data\"\n"
			+ "          },\n"
			+ "          \"asdf1\": {\n"
			+ "            \"B\": \"AAEqQQ==\"\n"
			+ "          },\n"
			+ "          \"b2\": {\n"
			+ "            \"B\": \"test\"\n"
			+ "          },\n"
			+ "          \"asdf2\": {\n"
			+ "            \"BS\": [\n"
			+ "              \"AAEqQQ==\",\n"
			+ "              \"QSoBAA==\",\n"
			+ "              \"AAEqQQ==\"\n"
			+ "            ]\n"
			+ "          },\n"
			+ "          \"key\": {\n"
			+ "            \"S\": \"binary\"\n"
			+ "          },\n"
			+ "          \"Binary\": {\n"
			+ "            \"B\": \"AAEqQQ==\"\n"
			+ "          },\n"
			+ "          \"Boolean\": {\n"
			+ "            \"BOOL\": true\n"
			+ "          },\n"
			+ "          \"BinarySet\": {\n"
			+ "            \"BS\": [\n"
			+ "              \"AAEqQQ==\",\n"
			+ "              \"AAEqQQ==\"\n"
			+ "            ]\n"
			+ "          },\n"
			+ "          \"List\": {\n"
			+ "            \"L\": [\n"
			+ "              {\n"
			+ "                \"S\": \"Cookies\"\n"
			+ "              },\n"
			+ "              {\n"
			+ "                \"S\": \"Coffee\"\n"
			+ "              },\n"
			+ "              {\n"
			+ "                \"N\": \"3.14159\"\n"
			+ "              }\n"
			+ "            ]\n"
			+ "          },\n"
			+ "          \"Map\": {\n"
			+ "            \"M\": {\n"
			+ "              \"Name\": {\n"
			+ "                \"S\": \"Joe\"\n"
			+ "              },\n"
			+ "              \"Age\": {\n"
			+ "                \"N\": \"35\"\n"
			+ "              }\n"
			+ "            }\n"
			+ "          },\n"
			+ "          \"FloatNumber\": {\n"
			+ "            \"N\": \"123.45\"\n"
			+ "          },\n"
			+ "          \"IntegerNumber\": {\n"
			+ "            \"N\": \"123\"\n"
			+ "          },\n"
			+ "          \"NumberSet\": {\n"
			+ "            \"NS\": [\n"
			+ "              \"1234\",\n"
			+ "              \"567.8\"\n"
			+ "            ]\n"
			+ "          },\n"
			+ "          \"Null\": {\n"
			+ "            \"NULL\": true\n"
			+ "          },\n"
			+ "          \"String\": {\n"
			+ "            \"S\": \"Hello\"\n"
			+ "          },\n"
			+ "          \"StringSet\": {\n"
			+ "            \"SS\": [\n"
			+ "              \"Giraffe\",\n"
			+ "              \"Zebra\"\n"
			+ "            ]\n"
			+ "          },\n"
			+ "          \"EmptyStringSet\": {\n"
			+ "            \"SS\": []\n"
			+ "          }\n"
			+ "        },\n"
			+ "        \"SequenceNumber\": \"1405400000000002063282832\",\n"
			+ "        \"SizeBytes\": 54,\n"
			+ "        \"StreamViewType\": \"NEW_AND_OLD_IMAGES\"\n"
			+ "      },\n"
			+ "      \"eventSourceARN\": \"arn:aws:dynamodb:us-east-1:123456789012:table/Example-Table/stream/2016-12-01T00:00:00.000\"\n"
			+ "    }\n"
			+ "  ]\n"
			+ "}";

	private List<String> maskedKeys = new ArrayList<>();

	@Test
	public void validateMasking() throws Exception {
		JacksonMapper mapper = new JacksonMapper(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT));
		Map<Object, Object> map = mapper.fromJson(event, Map.class);

		JsonMasker masker = JsonMasker.INSTANCE();
		String[] keysToMask = masker.getKeysToMask();
		assertThat(keysToMask).contains("eventSourceARN", "asdf1", "SS");

		String maskedJson = masker.mask(event);
		System.out.println(maskedJson);
		map = mapper.fromJson(maskedJson, Map.class);

		this.iterate(map, Arrays.asList(keysToMask));
		assertThat(maskedKeys.size()).isEqualTo(6);
		assertThat(maskedKeys.get(0)).isEqualTo("asdf1");
		assertThat(maskedKeys.get(1)).isEqualTo("eventSourceARN");
		assertThat(maskedKeys.get(2)).isEqualTo("asdf1");
		assertThat(maskedKeys.get(3)).isEqualTo("SS");
		assertThat(maskedKeys.get(4)).isEqualTo("SS");
		assertThat(maskedKeys.get(5)).isEqualTo("eventSourceARN");

		Field jsonMaskerField = ReflectionUtils.findField(JsonMasker.class, "jsonMasker");
		jsonMaskerField.setAccessible(true);
		jsonMaskerField.set(masker, null);
	}

	@Test
	public void validateMaskingWithAdditionalKeys() throws Exception {
		JacksonMapper mapper = new JacksonMapper(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT));
		Map<Object, Object> map = mapper.fromJson(event, Map.class);

		JsonMasker masker = JsonMasker.INSTANCE(Set.of("foo", "bar"));
		String[] keysToMask = masker.getKeysToMask();
		assertThat(keysToMask).contains("eventSourceARN", "asdf1", "SS", "foo", "bar");

		String maskedJson = masker.mask(event);
		System.out.println(maskedJson);
		map = mapper.fromJson(maskedJson, Map.class);

		this.iterate(map, Arrays.asList(keysToMask));
		assertThat(maskedKeys.size()).isEqualTo(6);
		assertThat(maskedKeys.get(0)).isEqualTo("asdf1");
		assertThat(maskedKeys.get(1)).isEqualTo("eventSourceARN");
		assertThat(maskedKeys.get(2)).isEqualTo("asdf1");
		assertThat(maskedKeys.get(3)).isEqualTo("SS");
		assertThat(maskedKeys.get(4)).isEqualTo("SS");
		assertThat(maskedKeys.get(5)).isEqualTo("eventSourceARN");

		Field jsonMaskerField = ReflectionUtils.findField(JsonMasker.class, "jsonMasker");
		jsonMaskerField.setAccessible(true);
		jsonMaskerField.set(masker, null);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void iterate(Object json, List keysToMask) {
		if (json instanceof Collection arrayValue) {
			for (Object element : arrayValue) {
				if (element instanceof Map mapElement) {
					for (Map.Entry<String, Object> entry : ((Map<String, Object>) mapElement).entrySet()) {
						this.doMask(entry.getKey(), entry, keysToMask);
					}
				}
			}
		}
		else if (json instanceof Map mapElement) {
			for (Map.Entry<String, Object> entry : ((Map<String, Object>) mapElement).entrySet()) {
				this.doMask(entry.getKey(), entry, keysToMask);
			}
		}
	}

	@SuppressWarnings("rawtypes")
	private void doMask(String key, Map.Entry<String, Object> entry, List keysToMask) {
		if (keysToMask.contains(key)) {
			System.out.println("Masked: " + entry.getKey());
			maskedKeys.add(key);
		}
		else if (entry.getValue() instanceof Map) {
			this.iterate(entry.getValue(), keysToMask);
		}
		else if (entry.getValue() instanceof Collection) {
			this.iterate(entry.getValue(), keysToMask);
		}
	}
}
