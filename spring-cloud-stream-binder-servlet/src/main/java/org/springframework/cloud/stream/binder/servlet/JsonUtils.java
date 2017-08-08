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

import java.util.ArrayList;
import java.util.List;

/**
 * Internal convenience class to help with JSON message bodies. In particular translating
 * between JSON arrays and lists of payloads.
 * 
 * @author Dave Syer
 *
 */
class JsonUtils {

	/**
	 * Split a JSON array into a list of individual objects, without parsing the objects
	 * themselves..
	 */
	public static List<String> split(String body) {
		body = body.trim();
		// it's an array
		List<String> strings = new ArrayList<>();
		int index = 0;
		int open = 0;
		boolean inString = false;
		StringBuilder builder = new StringBuilder();
		while (index++ < body.length() - 1) {
			char current = body.charAt(index);
			builder.append(current);
			if (body.charAt(index - 1) != '\\') {
				if (current == '"') {
					if (!inString) {
						open++;
						inString = true;
					}
					else {
						open--;
						inString = false;
					}
				}
				else if (current == '[') {
					open++;
				}
				else if (current == ']') {
					open--;
				}
				else if (current == '{') {
					open++;
				}
				else if (current == '}') {
					open--;
				}
			}
			if (open == 0) {
				if (builder.charAt(0) == '"') {
					builder.delete(0, 1);
					builder.delete(builder.length() - 1, builder.length());
				}
				strings.add(builder.toString());
				builder.setLength(0);
				while (index++ < body.length() - 1 && body.charAt(index) != ',') {
				}
				while (index++ < body.length() - 1
						&& Character.isWhitespace(body.charAt(index))) {
				}
				index--;
			}
		}
		return strings;
	}
}