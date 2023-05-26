/*
 * Copyright 2023-2023 the original author or authors.
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
	 * Blah.
	 */
	public List<String> get;


	/**
	 * Blah.
	 */
	public List<String> post;

	/**
	 * Blah.
	 */
	public List<String> put;

	/**
	 * Blah.
	 */
	public List<String> delete;

	public List<String> getGet() {
		return this.get;
	}

	public void setGet(List<String> get) {
		this.get = get;
	}

	public List<String> getPost() {
		return post;
	}

	public void setPost(List<String> post) {
		this.post = post;
	}

	public List<String> getPut() {
		return put;
	}

	public void setPut(List<String> put) {
		this.put = put;
	}

	public List<String> getDelete() {
		return delete;
	}

	public void setDelete(List<String> delete) {
		this.delete = delete;
	}
}
