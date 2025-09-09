/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.cloud.function.web;

import java.util.Collections;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.function.context.FunctionProperties;

/**
*
* @author Oleg Zhurakousky
* @since 4.0.4
*
*/
@ConfigurationProperties(prefix = FunctionProperties.PREFIX + ".http")
public class FunctionHttpProperties {

	/**
	 * Function definition mappings for GET method (e.g. 'spring.cloud.function.http.GET=foo;bar|baz')
	 */
	public String get;


	/**
	 * Function definition mappings for POST method (e.g. 'spring.cloud.function.http.POST=foo;bar|baz')
	 */
	public String post;

	/**
	 * Function definition mappings for PUT method (e.g. 'spring.cloud.function.http.PUT=foo;bar|baz')
	 */
	public String put;

	/**
	 * Function definition mappings for DELETE method (e.g. 'spring.cloud.function.http.DELETE=foo;bar|baz')
	 */
	public String delete;


	/**
	 * List of headers to be ignored when generating HttpHeaders (request or response).
	 */
	public List<String> ignoredHeaders = Collections.emptyList();

	/**
	 * List of headers that must remain only in the request.
	 */
	public List<String> requestOnlyHeaders = Collections.emptyList();

	public String getGet() {
		return this.get;
	}

	public void setGet(String get) {
		this.get = get;
	}

	public String getPost() {
		return post;
	}

	public void setPost(String post) {
		this.post = post;
	}

	public String getPut() {
		return put;
	}

	public void setPut(String put) {
		this.put = put;
	}

	public String getDelete() {
		return delete;
	}

	public void setDelete(String delete) {
		this.delete = delete;
	}

	public List<String> getIgnoredHeaders() {
		return ignoredHeaders;
	}

	public void setIgnoredHeaders(List<String> ignoredHeaders) {
		this.ignoredHeaders = ignoredHeaders;
	}

	public List<String> getRequestOnlyHeaders() {
		return requestOnlyHeaders;
	}

	public void setRequestOnlyHeaders(List<String> requestOnlyHeaders) {
		this.requestOnlyHeaders = requestOnlyHeaders;
	}
}
