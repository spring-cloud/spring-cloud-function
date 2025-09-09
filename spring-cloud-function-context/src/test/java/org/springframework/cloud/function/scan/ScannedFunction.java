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

package org.springframework.cloud.function.scan;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * @author Dave Syer
 *
 */
@Component("function")
public class ScannedFunction
		implements Function<Map<String, String>, Map<String, String>> {

	@Override
	public Map<String, String> apply(Map<String, String> m) {
		return m.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(),
				e -> e.getValue().toString().toUpperCase(Locale.ROOT)));
	}

}
