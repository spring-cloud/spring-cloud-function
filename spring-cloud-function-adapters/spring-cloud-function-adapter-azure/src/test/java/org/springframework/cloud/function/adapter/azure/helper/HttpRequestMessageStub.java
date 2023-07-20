package org.springframework.cloud.function.adapter.azure.helper;

import java.net.URI;
import java.util.Map;

import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpResponseMessage.Builder;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;

public class HttpRequestMessageStub<I> implements HttpRequestMessage<I> {

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