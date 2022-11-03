/*
 * Copyright 2012-2020 the original author or authors.
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public abstract class JsonMapper {

	private static Log logger = LogFactory.getLog(JsonMapper.class);

	@SuppressWarnings("unchecked")
	public <T> T fromJson(Object json, Type type) {
		if (json instanceof Collection<?>) {
			Collection<?> inputs = (Collection<?>) json;
			Type itemType = FunctionTypeUtils.getImmediateGenericType(type, 0);
			Collection<?> results = FunctionTypeUtils.getRawType(type).isAssignableFrom(List.class)
					? new ArrayList<>()
					: new HashSet<>();
			for (Object input : inputs) {
				results.add(this.doFromJson(input, itemType));
			}
			return (T) results;
		}
		else {
			if (!(json instanceof String) && !(json instanceof byte[]) && !(json instanceof Reader)) {
				json = this.toJson(json);
				if (FunctionTypeUtils.getRawType(type) == String.class) {
					return (T) new String((byte[]) json, StandardCharsets.UTF_8);
				}
				else if (FunctionTypeUtils.getRawType(type) == byte[].class) {
					return (T) json;
				}
			}
			if (json instanceof String && (String.class == type || byte[].class == type)) {
				return String.class == type ? (T) json : (T) ((String) json).getBytes(StandardCharsets.UTF_8);
			}
			else {
				return this.doFromJson(json, type);
			}
		}
	}

	protected abstract <T> T doFromJson(Object json, Type type);

	public byte[] toJson(Object value) {
		byte[] result = null;
		if (isJsonString(value)) {
			if (logger.isDebugEnabled()) {
				logger.debug(
						"String already represents JSON. Skipping conversion in favor of 'getBytes(StandardCharsets.UTF_8'.");
			}
			result = value instanceof byte[] ? (byte[]) value : ((String) value).getBytes(StandardCharsets.UTF_8);
		}
		else if (value instanceof byte[]) {
			result = (byte[]) value;
		}
		return result;
	}

	public abstract String toString(Object value);

	/**
	 * Performs a simple validation on an object to see if it appears to be a JSON string.
	 * NOTE: the validation is very rudimentary and simply checks that the object is a String and begins
	 * and ends with matching pairs of "{}" or "[]" or "\"\"" and therefore may not handle some corner cases.
	 * Primarily intended for internal of  the framework.
	 * @param value candidate object to evaluate
	 * @return true if and object appears to be a valid JSON string, otherwise false.
	 */
	public static boolean isJsonString(Object value) {
		boolean isJson = false;
		if (value instanceof byte[]) {
			value = new String((byte[]) value, StandardCharsets.UTF_8);
		}
		if (value instanceof String) {
			String str = ((String) value).trim();
			isJson = (str.startsWith("\"") && str.endsWith("\"")) ||
					(str.startsWith("{") && str.endsWith("}")) ||
					(str.startsWith("[") && str.endsWith("]"));
		}

		return isJson;
	}

	public static boolean isJsonStringRepresentsCollection(Object value) {
		boolean isJson = false;
		if (value instanceof byte[]) {
			value = new String((byte[]) value, StandardCharsets.UTF_8);
		}
		if (value instanceof String) {
			String str = ((String) value).trim();
			isJson = isJsonString(value) && str.startsWith("[") && str.endsWith("]");
		}
		return isJson;
	}

	public static boolean isJsonStringRepresentsMap(Object value) {
		boolean isJson = false;
		if (value instanceof byte[]) {
			value = new String((byte[]) value, StandardCharsets.UTF_8);
		}
		if (value instanceof String) {
			String str = ((String) value).trim();
			isJson = isJsonString(value) && str.startsWith("{") && str.endsWith("}");
		}
		return isJson;
	}
}
