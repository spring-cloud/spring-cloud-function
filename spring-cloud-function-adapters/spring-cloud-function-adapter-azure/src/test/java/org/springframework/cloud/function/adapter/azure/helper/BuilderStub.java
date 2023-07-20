package org.springframework.cloud.function.adapter.azure.helper;

import java.util.HashMap;
import java.util.Map;

import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatusType;
import com.microsoft.azure.functions.HttpResponseMessage.Builder;

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