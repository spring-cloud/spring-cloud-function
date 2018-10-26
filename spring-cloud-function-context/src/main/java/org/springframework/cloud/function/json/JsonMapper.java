/*
 * Copyright 2016-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.function.json;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.ResolvableType;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public interface JsonMapper {

	/**
	 * @deprecated since v2.0 in favor of {@link #toObject(String, Type)}
	 */
	@Deprecated
	default <T> List<T> toList(String json, Class<T> type) {
		Type actualType = (json.startsWith("[") && !List.class.isAssignableFrom(type))
				? ResolvableType.forClassWithGenerics(ArrayList.class, (Class<?>) type)
						.getType()
				: type;
		return toObject(json, actualType);
	}

	/**
	 * @since 2.0
	 */
	<T> T toObject(String json, Type type);

	/**
	 * @deprecated since v2.0 in favor of {@link #toObject(String, Type)}
	 */
	default <T> T toSingle(String json, Class<T> type) {
		return toObject(json, type);
	}

	String toString(Object value);

}
