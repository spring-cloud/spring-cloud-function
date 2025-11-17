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

package org.springframework.cloud.function.json;

import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.TypeFactory;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class JacksonMapper extends JsonMapper {

	private static Log logger = LogFactory.getLog(JacksonMapper.class);

	private final ObjectMapper mapper;

	public JacksonMapper(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	public void configureObjectMapper(Consumer<ObjectMapper> configurer) {
		configurer.accept(mapper);
	}

	public ObjectMapper getObjectMapper() {
		return this.mapper;
	}

	@Override
	protected <T> T doFromJson(Object json, Type type) {
		T convertedValue = null;
		JavaType constructType = TypeFactory.createDefaultInstance().constructType(type);

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
			throw new IllegalStateException(
					"Failed to convert. Possible bug as the conversion probably shouldn't have been attempted here", e);
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
				if (logger.isTraceEnabled()) {
					logger.trace("Failed to writeValueAsBytes: " + value, e);
				}
			}
		}
		return jsonBytes;
	}

	@Override
	public String toString(Object value) {
		try {
			return this.mapper.writeValueAsString(value);
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Cannot convert to JSON", e);
		}
	}

}
