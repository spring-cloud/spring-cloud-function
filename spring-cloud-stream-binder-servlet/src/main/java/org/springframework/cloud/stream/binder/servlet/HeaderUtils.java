/*
 * Copyright 2016-2017 the original author or authors.
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
package org.springframework.cloud.stream.binder.servlet;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.ObjectUtils;

/**
 * @author Dave Syer
 *
 */
class HeaderUtils {

	public static HttpHeaders fromMessage(Map<String, Object> headers,
			HttpHeaders request) {
		HttpHeaders result = new HttpHeaders();
		for (String name : headers.keySet()) {
			Object value = headers.get(name);
			name = name.toLowerCase();
			if (MessageHeaders.ID.equals(name)) {
				continue;
			}
			if (request.containsKey(name)) {
				if (name.startsWith("x-")) {
					if (!name.startsWith("x-forwarded")) {
						Collection<?> values = multi(value);
						for (Object object : values) {
							result.set(name, object.toString());
						}
					}
				}
			}
			else {
				Collection<?> values = multi(value);
				for (Object object : values) {
					result.set(name, object.toString());
				}
			}
		}
		return result;
	}

	private static Collection<?> multi(Object value) {
		if (value instanceof Collection) {
			Collection<?> collection = (Collection<?>) value;
			return collection;
		}
		else if (ObjectUtils.isArray(value)) {
			Object[] values = ObjectUtils.toObjectArray(value);
			return Arrays.asList(values);
		}
		return Arrays.asList(value);
	}

	public static MessageHeaders fromHttp(HttpHeaders headers) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (String name : headers.keySet()) {
			Collection<?> values = multi(headers.get(name));
			name = name.toLowerCase();
			Object value = values == null ? null
					: (values.size() == 1 ? values.iterator().next() : values);
			map.put(name, value);
		}
		return new MessageHeaders(map);
	}
}
