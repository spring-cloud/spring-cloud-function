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

import org.springframework.cloud.function.context.FunctionScan;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;

/**
 * @author Mark Fisher
 */
@FunctionScan
@RestController
public class ActionController extends FunctionInitializer {

	private final ObjectMapper objectMapper = new ObjectMapper();

	public ActionController() {
		super();
	}

	@PostMapping("/init")
	public void init(@RequestBody InitRequest request) {
		initialize();
	}

	@PostMapping(value="/run", consumes="application/json", produces="application/json")
	public Object run(@RequestBody ActionRequest request) {
		Object input = convertEvent(request.getValue());
		Flux<?> output = apply(extract(input));
		Object result = result(input, output);
		try {
			return "{\"result\":" + this.objectMapper.writeValueAsString(result) + "}";
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("failed to write JSON response", e);
		}
	}

	private Object result(Object input, Flux<?> output) {
		List<Object> result = new ArrayList<>();
		for (Object value : output.toIterable()) {
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

	protected Object convertEvent(Map<String, String> event) {
		// just expecting "payload" for now
		return event.get("payload");
	}
}
