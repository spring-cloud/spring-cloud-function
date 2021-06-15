/*
 * Copyright 2021-2021 the original author or authors.
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

import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * For internal use only.
 *
 *
 * @author Oleg Zhurakousky
 *
 */
public class FunctionWrapper {
	private final FunctionInvocationWrapper function;

	private final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

	private HttpHeaders headers = new HttpHeaders();

	private Object argument;

	public FunctionWrapper(FunctionInvocationWrapper function) {
		this.function = function;
	}

	public HttpHeaders getHeaders() {
		return headers;
	}

	public void setHeaders(HttpHeaders headers) {
		this.headers = headers;
	}

	public Object getArgument() {
		return argument;
	}

	public void setArgument(Object argument) {
		this.argument = argument;
	}

	public FunctionInvocationWrapper getFunction() {
		return function;
	}

	public MultiValueMap<String, String> getParams() {
		return params;
	}
}
