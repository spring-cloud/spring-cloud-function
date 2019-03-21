/*
 * Copyright 2012-2019 the original author or authors.
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

import org.springframework.cloud.function.web.util.HeaderUtils;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

/**
 * @author Dave Syer
 *
 */
class SimpleRequestBuilder implements RequestBuilder {

	private String baseUrl = "http://${destination}";

	private Map<String, String> headers = new LinkedHashMap<>();

	private Environment environment;

	SimpleRequestBuilder(Environment environment) {
		this.environment = environment;
	}

	@Override
	public HttpHeaders headers(String destination, Object value) {
		MessageHeaders incoming = new MessageHeaders(Collections.emptyMap());
		if (value instanceof Message) {
			Message<?> message = (Message<?>) value;
			incoming = message.getHeaders();
		}
		HttpHeaders result = HeaderUtils.fromMessage(incoming);
		for (String key : this.headers.keySet()) {
			String header = this.headers.get(key);
			header = header.replace("${destination}", destination);
			header = this.environment.resolvePlaceholders(header);
			result.set(key, header);
		}
		return result;
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
