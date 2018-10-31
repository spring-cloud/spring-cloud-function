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
package org.springframework.cloud.function.web.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.MessageHeaders;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class HeaderUtils {

	private static HttpHeaders IGNORED = new HttpHeaders();

	private static HttpHeaders REQUEST_ONLY = new HttpHeaders();

	static {
		IGNORED.add(MessageHeaders.ID, "");
		IGNORED.add(HttpHeaders.CONTENT_LENGTH, "0");
		// Headers that would typically be added by a downstream client
		REQUEST_ONLY.add(HttpHeaders.ACCEPT, "");
		REQUEST_ONLY.add(HttpHeaders.CONTENT_LENGTH, "");
		REQUEST_ONLY.add(HttpHeaders.CONTENT_TYPE, "");
		REQUEST_ONLY.add(HttpHeaders.HOST, "");
	}

	public static HttpHeaders fromMessage(MessageHeaders headers) {
		HttpHeaders result = new HttpHeaders();
		for (String name : headers.keySet()) {
			Object value = headers.get(name);
			name = name.toLowerCase();
			if (!IGNORED.containsKey(name)) {
				Collection<?> values = multi(value);
				for (Object object : values) {
					result.set(name, object.toString());
				}
			}
		}
		return result;
	}

	public static HttpHeaders sanitize(HttpHeaders request) {
		HttpHeaders result = new HttpHeaders();
		for (String name : request.keySet()) {
			List<String> value = request.get(name);
			name = name.toLowerCase();
			if (!IGNORED.containsKey(name) && !REQUEST_ONLY.containsKey(name)) {
				result.put(name, value);
			}
		}
		return result;
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

	private static Collection<?> multi(Object value) {
		return value instanceof Collection ? (Collection<?>) value : Arrays.asList(value);
	}
}
