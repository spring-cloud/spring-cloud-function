/*
 * Copyright 2024-present the original author or authors.
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

import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonMaskerTests {

	private String event = """
			{
			  "Records": [
			    {
			      "eventID": "f07f8ca4b0b26cb9c4e5e77e69f274ee",
			      "eventName": "INSERT",
			      "eventVersion": "1.1",
			      "eventSource": "aws:dynamodb",
			      "awsRegion": "us-east-1",
			      "userIdentity":{
			        "type":"Service",
			        "principalId":"dynamodb.amazonaws.com"
			      },
			      "dynamodb": {
			        "ApproximateCreationDateTime": 1.684934517E9,
			        "Keys": {
			          "val": {
			            "S": "data"
			          },
			          "key": {
			            "S": "binary"
			          }
			        },
			        "NewImage": {
			          "val": {
			            "S": "data"
			          },
			          "asdf1": {
			            "B": "AAEqQQ=="
			          },
			          "asdf2": {
			            "BS": [
			              "AAEqQQ==",
			              "QSoBAA=="
			            ]
			          },
			          "key": {
			            "S": "binary"
			          }
			        },
			        "SequenceNumber": "1405400000000002063282832",
			        "SizeBytes": 54,
			        "StreamViewType": "NEW_AND_OLD_IMAGES"
			      },
			      "eventSourceARN": "arn:aws:dynamodb:us-east-1:123456789012:table/Example-Table/stream/2016-12-01T00:00:00.000"
			    },
			    {
			      "eventID": "f07f8ca4b0b26cb9c4e5e77e42f274ee",
			      "eventName": "INSERT",
			      "eventVersion": "1.1",
			      "eventSource": "aws:dynamodb",
			      "awsRegion": "us-east-1",
			      "dynamodb": {
			        "ApproximateCreationDateTime": 1480642020,
			        "Keys": {
			          "val": {
			            "S": "data"
			          },
			          "key": {
			            "S": "binary"
			          }
			        },
			        "NewImage": {
			          "val": {
			            "S": "data"
			          },
			          "asdf1": {
			            "B": "AAEqQQ=="
			          },
			          "b2": {
			            "B": "test"
			          },
			          "asdf2": {
			            "BS": [
			              "AAEqQQ==",
			              "QSoBAA==",
			              "AAEqQQ=="
			            ]
			          },
			          "key": {
			            "S": "binary"
			          },
			          "Binary": {
			            "B": "AAEqQQ=="
			          },
			          "Boolean": {
			            "BOOL": true
			          },
			          "BinarySet": {
			            "BS": [
			              "AAEqQQ==",
			              "AAEqQQ=="
			            ]
			          },
			          "List": {
			            "L": [
			              {
			                "S": "Cookies"
			              },
			              {
			                "S": "Coffee"
			              },
			              {
			                "N": "3.14159"
			              }
			            ]
			          },
			          "Map": {
			            "M": {
			              "Name": {
			                "S": "Joe"
			              },
			              "Age": {
			                "N": "35"
			              }
			            }
			          },
			          "FloatNumber": {
			            "N": "123.45"
			          },
			          "IntegerNumber": {
			            "N": "123"
			          },
			          "NumberSet": {
			            "NS": [
			              "1234",
			              "567.8"
			            ]
			          },
			          "Null": {
			            "NULL": true
			          },
			          "String": {
			            "S": "Hello"
			          },
			          "StringSet": {
			            "SS": [
			              "Giraffe",
			              "Zebra"
			            ]
			          },
			          "EmptyStringSet": {
			            "SS": []
			          }
			        },
			        "SequenceNumber": "1405400000000002063282832",
			        "SizeBytes": 54,
			        "StreamViewType": "NEW_AND_OLD_IMAGES"
			      },
			      "eventSourceARN": "arn:aws:dynamodb:us-east-1:123456789012:table/Example-Table/stream/2016-12-01T00:00:00.000"
			    }
			  ]
			}""";

	private List<String> maskedKeys = new ArrayList<>();

	@Test
	public void validateMasking() throws Exception {
		ObjectMapper objectMapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
		JacksonMapper mapper = new JacksonMapper(objectMapper);
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
		ObjectMapper objectMapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
		JacksonMapper mapper = new JacksonMapper(objectMapper);
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
