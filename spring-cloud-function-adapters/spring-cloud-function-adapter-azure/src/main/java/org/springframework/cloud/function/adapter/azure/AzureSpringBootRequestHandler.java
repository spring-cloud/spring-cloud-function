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

package org.springframework.cloud.function.adapter.azure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.microsoft.azure.serverless.functions.ExecutionContext;
import reactor.core.publisher.Flux;

/**
 * @author Soby Chacko
 */
public class AzureSpringBootRequestHandler<I,O> extends AzureSpringFunctionInitializer {

	public Object handleRequest(I foo, ExecutionContext context) {
		context.getLogger().info("AHandlers's Java HTTP trigger processed a request.");
		initialize(context);

		Object convertedEvent = convertEvent(foo);
		Flux<?> output = apply(extract(convertedEvent));
		return result(convertedEvent, output);
	}

	protected Object convertEvent(I input) {
		return input;
	}

	private Flux<?> extract(Object input) {
		if (input instanceof Collection) {
			return Flux.fromIterable((Iterable<?>) input);
		}
		return Flux.just(input);
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


}
