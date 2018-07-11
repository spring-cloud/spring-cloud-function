/*
 * Copyright 2017-1018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.adapter.aws;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class SpringBootStreamHandler extends SpringFunctionInitializer
		implements RequestStreamHandler {

	@Autowired(required=false)
	private ObjectMapper mapper;

	public SpringBootStreamHandler() {
		super();
	}

	public SpringBootStreamHandler(Class<?> configurationClass) {
		super(configurationClass);
	}

	@Override
	protected void initialize() {
		super.initialize();
		if (this.mapper == null) {
			this.mapper = new ObjectMapper();
		}
	}

	@Override
	public void handleRequest(InputStream input, OutputStream output, Context context)
			throws IOException {
		initialize();
		Object value = convertStream(input);
		Publisher<?> flux = apply(extract(value));
		mapper.writeValue(output, result(value, flux));
	}

	private Object result(Object input, Publisher<?> flux) {
		List<Object> result = new ArrayList<>();
		for (Object value : Flux.from(flux).toIterable()) {
			result.add(value);
		}
		if (isSingleValue(input) && result.size()==1) {
			return result.get(0);
		}
		return result;
	}

	private boolean isSingleValue(Object input) {
		return !(input instanceof Collection);
	}

	private Flux<?> extract(Object input) {
		if (input instanceof Collection) {
			return Flux.fromIterable((Iterable<?>) input);
		}
		return Flux.just(input);
	}

	private Object convertStream(InputStream input) {
		try {
			return mapper.readValue(input, getInputType());
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot convert event", e);
		}
	}
}
