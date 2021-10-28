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

package org.springframework.cloud.function.json;

import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class JacksonMapper extends JsonMapper {

	private final ObjectMapper mapper;

	public JacksonMapper(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public <T> T toObject(String json, Type type) {
		return this.fromJson(json, type);
	}

	public void configureObjectMapper(Consumer<ObjectMapper> configurer) {
		configurer.accept(mapper);
	}

	@Override
	protected <T> T doFromJson(Object json, Type type) {
		T convertedValue = null;
		JavaType constructType = TypeFactory.defaultInstance().constructType(type);

		try {
			if (json instanceof String) {
				convertedValue = this.mapper.readValue((String) json, constructType);
			}
			else if (json instanceof byte[]) {
				convertedValue = this.mapper.readValue((byte[]) json, constructType);
			}
			else if (json instanceof Reader) {
				convertedValue = this.mapper.readValue((Reader) json, constructType);
			}
			else if (json instanceof Map) {
				convertedValue = this.mapper.convertValue(json, constructType);
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to convert. Possible bug as the conversion probably shouldn't have been attampted here", e);
		}
		return convertedValue;
	}

	@Override
	public byte[] toJson(Object value) {
		byte[] jsonBytes = super.toJson(value);
		if (jsonBytes == null) {
			try {
				jsonBytes = this.mapper.writeValueAsBytes(value);
			}
			catch (Exception e) {
				//ignore and let other converters have a chance
			}
		}
		return jsonBytes;
	}

	@Override
	public String toString(Object value) {
		try {
			return this.mapper.writeValueAsString(value);
		}
		catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Cannot convert to JSON", e);
		}
	}



}
