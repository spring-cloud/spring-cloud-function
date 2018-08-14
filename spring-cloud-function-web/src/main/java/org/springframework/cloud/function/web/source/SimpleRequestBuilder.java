/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.cloud.function.web.source;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;

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
		// TODO: add message headers if any
		HttpHeaders result = new HttpHeaders();
		for (String key : headers.keySet()) {
			String header = headers.get(key);
			header = header.replace("${destination}", destination);
			header = environment.resolvePlaceholders(header);
			result.add(key, header);
		}
		return result;
	}

	@Override
	public URI uri(String destination) {
		try {
			return new URI(environment
					.resolvePlaceholders(baseUrl.replace("${destination}", destination)));
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
