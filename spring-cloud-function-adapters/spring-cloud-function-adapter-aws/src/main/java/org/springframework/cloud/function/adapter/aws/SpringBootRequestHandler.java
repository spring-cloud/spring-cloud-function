/*
 * Copyright 2017 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;

/**
 * @author Mark Fisher
 */
public class SpringBootRequestHandler<E, O> extends SpringFunctionInitializer
		implements RequestHandler<E, Object> {

	public SpringBootRequestHandler(Class<?> configurationClass) {
		super(configurationClass);
	}

	public SpringBootRequestHandler() {
		super();
	}

	@Override
	public Object handleRequest(E event, Context context) {
		initialize();
		Object input = convertEvent(event);
		Publisher<?> output = apply(extract(input));
		return result(input, output);
	}

	private Object result(Object input, Publisher<?> output) {
		List<O> result = new ArrayList<>();
		for (Object value : Flux.from(output).toIterable()) {
			result.add(convertOutput(value));
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

	protected Object convertEvent(E event) {
		return event;
	}

	@SuppressWarnings("unchecked")
	protected O convertOutput(Object output) {
		return (O) output;
	}

}
