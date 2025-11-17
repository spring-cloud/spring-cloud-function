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

package org.springframework.cloud.function.adapter.azure.helper;

import java.util.HashMap;
import java.util.Map;

import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatusType;

public class HttpResponseMessageStub implements HttpResponseMessage {

	private HttpStatusType status;

	private Map<String, String> headers = new HashMap<>();

	private Object body;

	public HttpResponseMessageStub(HttpStatusType status, Map<String, String> headers, Object body) {
		this.status = status;
		this.headers = headers;
		this.body = body;
	}

	@Override
	public HttpStatusType getStatus() {
		return this.status;
	}

	@Override
	public String getHeader(String key) {
		return this.headers.get(key);
	}

	@Override
	public Object getBody() {
		return this.body;
	}

}
