/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.adapter.openwhisk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

/**
 * @author Mark Fisher
 * @author Kamesh Sampath
 */
@RestController
public class OpenWhiskActionHandler extends OpenWhiskFunctionInitializer {

	private static final String NO_INPUT_PROVIDED = "No input provided";
	private static Log logger = LogFactory.getLog(OpenWhiskFunctionInitializer.class);

	@Autowired
	private ObjectMapper mapper;

	public OpenWhiskActionHandler() {
		super();
	}

	@PostMapping("/init")
	public void init(@RequestBody OpenWhiskInitRequest request) {
		initialize();
	}

	@PostMapping(value = "/run", consumes = "application/json", produces = "application/json")
	public Object run(@RequestBody OpenWhiskActionRequest request) {
		Object input = convertEvent(request.getValue());
		Object result = NO_INPUT_PROVIDED;
		if(input !=null ) {
			Publisher<?> output = apply(extract(input));
			result = result(input, output);
		}
		return serializeBody(result);
	}

	private Object result(Object input, Publisher<?> output) {
		List<Object> result = new ArrayList<>();
		for (Object value : Flux.from(output).toIterable()) {
			result.add(value);
		}
		if (isSingleValue(input) && result.size() == 1) {
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

	protected Object convertEvent(Map<String, Object> value) {
		// just expecting "payload" for now
		if (logger.isDebugEnabled()) {
			logger.info("Action Request Value:" + value);
		}
		if (value != null) {
			Object payload = value.get("payload");
			return convertToFunctionParamType(payload);
		}
		return null;
	}

	private Object convertToFunctionParamType(Object payload) {
		try {
			return mapper.convertValue(payload, getInputType());
		} catch (Exception e) {
			throw new IllegalStateException("Cannot convert event payload", e);
		}
	}

	private String serializeBody(Object body) {
		try {
			return "{\"result\":" + mapper.writeValueAsString(body) + "}";
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Cannot convert output", e);
		}
	}
}
