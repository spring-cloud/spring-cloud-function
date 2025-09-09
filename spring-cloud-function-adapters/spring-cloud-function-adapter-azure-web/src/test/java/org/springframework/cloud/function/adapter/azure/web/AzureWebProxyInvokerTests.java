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

package org.springframework.cloud.function.adapter.azure.web;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpResponseMessage.Builder;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;
import org.junit.jupiter.api.Test;

public class AzureWebProxyInvokerTests {

	@Test
	public void test() throws Exception {
		System.setProperty("MAIN_CLASS", PetStoreSpringAppConfig.class.getName());
		AzureWebProxyInvoker proxyInvoker = new AzureWebProxyInvoker();
		AzureWebProxyInvoker instance = proxyInvoker.getInstance(AzureWebProxyInvoker.class);

		HttpRequestMessageStub<Optional<String>> request = new HttpRequestMessageStub<Optional<String>>();

		request.setHttpMethod(HttpMethod.GET);

		request.setUri(new URI(
				"http://localhost:7072/api/AzureWebAdapter/pets"));

		request.setBody(Optional.of("{\"id\":\"535932f1-d18b-488a-ad8f-8d50b9678492\"" +
				"\"breed\":\"Beagle\",\"name\":\"Murphy\",\"dateOfBirth\":1591682824313}"));

		HttpResponseMessage response = instance.execute(request, new TestExecutionContext("execute"));

		System.out.println(response.getBody());

	}

	public static class HttpRequestMessageStub<I> implements HttpRequestMessage<I> {

		private URI uri;
		private HttpMethod httpMethod;
		private Map<String, String> headers;
		private Map<String, String> queryParameters;
		private I body;

		public void setUri(URI uri) {
			this.uri = uri;
		}

		public void setHttpMethod(HttpMethod httpMethod) {
			this.httpMethod = httpMethod;
		}

		public void setHeaders(Map<String, String> headers) {
			this.headers = headers;
		}

		public void setQueryParameters(Map<String, String> queryParameters) {
			this.queryParameters = queryParameters;
		}

		public void setBody(I body) {
			this.body = body;
		}

		@Override
		public URI getUri() {
			return this.uri;
		}

		@Override
		public HttpMethod getHttpMethod() {
			return this.httpMethod;
		}

		@Override
		public Map<String, String> getHeaders() {
			return this.headers;
		}

		@Override
		public Map<String, String> getQueryParameters() {
			return this.queryParameters;
		}

		@Override
		public I getBody() {
			return this.body;
		}

		@Override
		public HttpResponseMessage.Builder createResponseBuilder(HttpStatusType status) {
			return new BuilderStub().status(status);
		}

		@Override
		public Builder createResponseBuilder(HttpStatus status) {
			return new BuilderStub().status(status);
		}

	}

	public static class BuilderStub implements Builder {

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

	public static class HttpResponseMessageStub implements HttpResponseMessage {

		private HttpStatusType status;
		private Map<String, String> headers = new HashMap<>();
		private Object body;

		HttpResponseMessageStub(HttpStatusType status, Map<String, String> headers,
				Object body) {
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
}
