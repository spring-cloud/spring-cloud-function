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

package org.springframework.cloud.function.web.source;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.cloud.function.web.FunctionHttpProperties;
import org.springframework.cloud.function.web.util.HeaderUtils;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

/**
 * @author Dave Syer
 * @author Oleg Zhurkousky
 *
 */
class SimpleRequestBuilder implements RequestBuilder {

	private static final String DESTINATION_PLACEHOLDER = "${destination}";

	private String baseUrl = "http://${destination}";

	private final Map<String, String> headers = new LinkedHashMap<>();

	private final Environment environment;

	private final FunctionHttpProperties httpProperties;

	SimpleRequestBuilder(Environment environment, FunctionHttpProperties httpProperties) {
		this.environment = environment;
		this.httpProperties = httpProperties;
	}

	@Override
	public HttpHeaders headers(String destination, Object value) {
		MessageHeaders incoming = new MessageHeaders(Collections.emptyMap());
		if (value instanceof Message) {
			Message<?> message = (Message<?>) value;
			incoming = message.getHeaders();
		}
		HttpHeaders result = HeaderUtils.fromMessage(incoming, this.httpProperties.getIgnoredHeaders());
		for (String key : this.headers.keySet()) {
			String header = this.headers.get(key);
			result.set(key, resolve(header, destination));
		}
		return result;
	}

	/*
	 * Resolves the placeholders of a header value from the environment and replaces
	 * ${destination} with the destination. The destination is substituted last and is
	 * never resolved itself, otherwise a destination that contains a placeholder would
	 * expose the values of the environment.
	 */
	private String resolve(String header, String destination) {
		String[] parts = header.split(Pattern.quote(DESTINATION_PLACEHOLDER), -1);
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				result.append(destination);
			}
			result.append(this.environment.resolvePlaceholders(parts[i]));
		}
		return result.toString();
	}

	@Override
	public URI uri(String destination) {
		try {
			return new URI(this.baseUrl.replace("${destination}", destination)
					.replace("{{destination}}", destination));
		}
		catch (URISyntaxException e) {
			throw new IllegalStateException("Cannot create URI", e);
		}
	}

	public void setTemplateUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public void setHeaders(Map<String, String> headers) {
		this.headers.putAll(headers);
	}

}
