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
import com.microsoft.azure.functions.HttpResponseMessage.Builder;
import com.microsoft.azure.functions.HttpStatusType;

public class BuilderStub implements Builder {

	private HttpStatusType status;

	private Map<String, String> headers = new HashMap<>();

	private Object body;

	@Override
	public Builder status(HttpStatusType status) {
		this.status = status;
		return this;
	}

	@Override
	public Builder header(String key, String value) {
		headers.put(key, value);
		return this;
	}

	@Override
	public Builder body(Object body) {
		this.body = body;
		return this;
	}

	@Override
	public HttpResponseMessage build() {
		return new HttpResponseMessageStub(this.status, this.headers, this.body);
	}

}
