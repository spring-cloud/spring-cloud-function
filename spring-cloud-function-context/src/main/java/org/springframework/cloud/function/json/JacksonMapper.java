/*
 * Copyright 2016-2017 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.cloud.function.json.JsonMapper;

/**
 * @author Dave Syer
 *
 */
public class JacksonMapper implements JsonMapper {

	private final ObjectMapper mapper;

	public JacksonMapper(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public <T> List<T> toList(String json, Class<T> type) {
		try {
			return mapper.readValue(json, mapper.getTypeFactory()
					.constructCollectionLikeType(ArrayList.class, type));
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Cannot convert JSON", e);
		}
	}

	@Override
	public <T> T toSingle(String json, Class<T> type) {
		try {
			return mapper.readValue(json, type);
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Cannot convert JSON", e);
		}
	}

	@Override
	public String toString(Object value) {
		try {
			return mapper.writeValueAsString(value);
		}
		catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Cannot convert to JSON", e);
		}
	}
}
