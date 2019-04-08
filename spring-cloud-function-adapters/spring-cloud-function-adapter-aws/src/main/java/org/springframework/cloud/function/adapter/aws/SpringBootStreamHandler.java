/*
 * Copyright 2017-2019 the original author or authors.
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

package org.springframework.cloud.function.adapter.aws;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.function.context.AbstractSpringFunctionAdapterInitializer;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class SpringBootStreamHandler extends AbstractSpringFunctionAdapterInitializer<Context>
		implements RequestStreamHandler {

	@Autowired(required = false)
	private ObjectMapper mapper;

	public SpringBootStreamHandler() {
		super();
	}

	public SpringBootStreamHandler(Class<?> configurationClass) {
		super(configurationClass);
	}

	@Override
	public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
		initialize(context);
		Object value = convertStream(input);
		Publisher<?> flux = apply(extract(value));
		this.mapper.writeValue(output, result(value, flux));
	}

	@Override
	protected void initialize(Context context) {
		super.initialize(context);
		if (this.mapper == null) {
			this.mapper = new ObjectMapper();
		}
	}

	private Flux<?> extract(Object input) {
		if (input instanceof Collection) {
			return Flux.fromIterable((Iterable<?>) input);
		}
		return Flux.just(input);
	}

	/*
	 * Will convert to POJOP or generic map unless user
	 * explicitly requests InputStream (e.g., Function<InputStream, ?>).
	 */
	private Object convertStream(InputStream input) {
		Object convertedResult = input;
		try {
			Class<?> inputType = getInputType();
			if (!InputStream.class.isAssignableFrom(inputType)) {
				convertedResult = this.mapper.readValue(input, inputType);
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot convert event stream", e);
		}
		return convertedResult;
	}

}
