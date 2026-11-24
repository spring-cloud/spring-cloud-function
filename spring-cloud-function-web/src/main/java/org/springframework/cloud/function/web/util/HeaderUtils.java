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

package org.springframework.cloud.function.web.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.MessageHeaders;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public final class HeaderUtils {

	/**
	 * Message Header name which contains HTTP request parameters.
	 */
	public static final String HTTP_REQUEST_PARAM = "http_request_param";

	private static final HttpHeaders IGNORED = new HttpHeaders();

	private static final HttpHeaders REQUEST_ONLY = new HttpHeaders();

	static {
		IGNORED.add(MessageHeaders.ID, "");
		IGNORED.add(HttpHeaders.CONTENT_LENGTH, "0");
		IGNORED.add(HttpHeaders.TRANSFER_ENCODING, "*");
		IGNORED.add(HttpHeaders.SET_COOKIE, "*");
		IGNORED.add(HttpHeaders.SET_COOKIE2, "*");
		IGNORED.add("Content-Security-Policy", "*");
		IGNORED.add(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "*");
		IGNORED.add(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "*");
		IGNORED.add(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "*");
		IGNORED.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
		IGNORED.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "*");
		IGNORED.add(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "*");
		IGNORED.add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "*");
		IGNORED.add(HttpHeaders.AUTHORIZATION, "*");
		IGNORED.add(HttpHeaders.CACHE_CONTROL, "*");
		IGNORED.add(HttpHeaders.COOKIE, "*");
		IGNORED.add(HttpHeaders.PROXY_AUTHENTICATE, "*");
		IGNORED.add(HttpHeaders.PROXY_AUTHORIZATION, "*");

		// Headers that would typically be added by a downstream client
		REQUEST_ONLY.add(HttpHeaders.ACCEPT, "");
		REQUEST_ONLY.add(HttpHeaders.CONTENT_LENGTH, "");
		REQUEST_ONLY.add(HttpHeaders.CONTENT_TYPE, "");
		REQUEST_ONLY.add(HttpHeaders.HOST, "");

		// Headers that change how a response is interpreted or processed by the client
		// and must not be echoed back from a request
		REQUEST_ONLY.add(HttpHeaders.CONTENT_ENCODING, "");
		REQUEST_ONLY.add(HttpHeaders.CONTENT_DISPOSITION, "");
		REQUEST_ONLY.add(HttpHeaders.CONTENT_LOCATION, "");
		REQUEST_ONLY.add(HttpHeaders.CONTENT_RANGE, "");
		REQUEST_ONLY.add(HttpHeaders.LOCATION, "");
		REQUEST_ONLY.add("Refresh", "");
		REQUEST_ONLY.add(HttpHeaders.LINK, "");
		REQUEST_ONLY.add(HttpHeaders.WWW_AUTHENTICATE, "");

		// Hop-by-hop headers
		REQUEST_ONLY.add(HttpHeaders.CONNECTION, "");
		REQUEST_ONLY.add("Keep-Alive", "");
		REQUEST_ONLY.add(HttpHeaders.UPGRADE, "");
		REQUEST_ONLY.add(HttpHeaders.TE, "");
		REQUEST_ONLY.add(HttpHeaders.TRAILER, "");
	}

	private HeaderUtils() {
		throw new IllegalStateException("Can't instantiate a utility class");
	}

	/**
	 * Check if the header name is reserved for the framework (the HTTP request parameters
	 * header, the {@code scf-} headers and the {@code lambda} headers used by the
	 * adapters), in which case a value received from a client must not be trusted.
	 * @param name header name
	 * @return true if the name is reserved
	 */
	public static boolean isReservedHeader(String name) {
		return HTTP_REQUEST_PARAM.equalsIgnoreCase(name)
				|| name.regionMatches(true, 0, "scf-", 0, 4)
				|| name.regionMatches(true, 0, "lambda", 0, 6);
	}

	private static boolean containsIgnoreCase(List<String> names, String name) {
		for (String candidate : names) {
			if (name.equalsIgnoreCase(candidate)) {
				return true;
			}
		}
		return false;
	}

	public static HttpHeaders fromMessage(MessageHeaders headers, List<String> ignoredHeders) {
		HttpHeaders result = new HttpHeaders();
		for (String name : headers.keySet()) {
			Object value = headers.get(name);
			name = name.toLowerCase(Locale.ROOT);
			if (!IGNORED.containsHeader(name) && !containsIgnoreCase(ignoredHeders, name)) {
				Collection<?> values = multi(value);
				for (Object object : values) {
					result.set(name, object.toString());
				}
			}
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	public static HttpHeaders fromMessage(MessageHeaders headers) {
		return fromMessage(headers, Collections.EMPTY_LIST);
	}


	public static HttpHeaders sanitize(HttpHeaders request, List<String> ignoredHeders, List<String> requestOnlyHeaders) {
		HttpHeaders result = new HttpHeaders();
		for (String name : request.headerNames()) {
			List<String> value = request.get(name);
			name = name.toLowerCase(Locale.ROOT);
			if (!IGNORED.containsHeader(name) && !REQUEST_ONLY.containsHeader(name) && !containsIgnoreCase(ignoredHeders, name) && !containsIgnoreCase(requestOnlyHeaders, name)) {
				result.put(name, value);
			}
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	public static HttpHeaders sanitize(HttpHeaders request) {
		return sanitize(request, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
	}

	public static MessageHeaders fromHttp(HttpHeaders headers) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (String name : headers.headerNames()) {
			Collection<?> values = multi(headers.get(name));
			name = name.toLowerCase(Locale.ROOT);
			Object value = values == null ? null
					: (values.size() == 1 ? values.iterator().next() : values);
			if (name.toLowerCase(Locale.ROOT).equals(HttpHeaders.CONTENT_TYPE.toLowerCase(Locale.ROOT))) {
				name = MessageHeaders.CONTENT_TYPE;
			}
			map.put(name, value);
		}
		return new MessageHeaders(map);
	}

	private static Collection<?> multi(Object value) {
		return value instanceof Collection ? (Collection<?>) value : Arrays.asList(value);
	}

}
