package org.springframework.cloud.function.adapter.azure.helper;

import java.util.HashMap;
import java.util.Map;

import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatusType;

public class HttpResponseMessageStub implements HttpResponseMessage {

	private HttpStatusType status;
	private Map<String, String> headers = new HashMap<>();
	private Object body;

	public HttpResponseMessageStub(HttpStatusType status, Map<String, String> headers,
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