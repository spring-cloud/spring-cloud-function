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

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;

import org.springframework.core.ResolvableType;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public abstract class JsonMapper {

	private static Log logger = LogFactory.getLog(JsonMapper.class);

	/**
	 * @param <T>  type for list arguments
	 * @param json JSON input
	 * @param type type of list arguments
	 * @return list of elements
	 * @deprecated since v2.0 in favor of {@link #toObject(String, Type)}
	 */
	@Deprecated
	<T> List<T> toList(String json, Class<T> type) {
		Type actualType = (json.startsWith("[") && !List.class.isAssignableFrom(type))
				? ResolvableType.forClassWithGenerics(ArrayList.class, (Class<?>) type).getType()
				: type;
		return toObject(json, actualType);
	}

	/**
	 * @param <T>  return type
	 * @param json JSON input
	 * @param type type
	 * @return object
	 * @since 2.0
	 * @deprecated since v3.0.4 in favor of {@link #fromJson(Object, Type)}
	 */
	@Deprecated
	abstract <T> T toObject(String json, Type type);

	public abstract <T> T fromJson(Object json, Type type);

	public byte[] toJson(Object value) {
		if (value instanceof String) {
			try {
				new JSONObject((String) value);
				if (logger.isDebugEnabled()) {
					logger.debug(
							"String already represents JSON. Skipping conversion in favor of 'getBytes(StandardCharsets.UTF_8'.");
				}
				return ((String) value).getBytes(StandardCharsets.UTF_8);
			}
			catch (Exception ex) {
				// ignore
			}
		}
		return null;
	}

	/**
	 * @param <T>  type for list arguments
	 * @param json JSON input
	 * @param type type of list arguments
	 * @return single object
	 * @deprecated since v2.0 in favor of {@link #toObject(String, Type)}
	 */
	@Deprecated
	<T> T toSingle(String json, Class<T> type) {
		return toObject(json, type);
	}

	public abstract String toString(Object value);

}
