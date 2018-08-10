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

import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;

/**
 * @author Soby Chacko
 */
public class AzureSpringBootRequestHandler<I, O> extends AzureSpringFunctionInitializer {

	public AzureSpringBootRequestHandler(Class<?> configurationClass) {
		super(configurationClass);
	}

	public AzureSpringBootRequestHandler() {
		super();
	}

	public O handleRequest(I input, ExecutionContext context) {
		try {
			if (context != null) {
				context.getLogger().fine("Handler processed a request.");
			}
			initialize(context);

			Object convertedEvent = convertEvent(input);
			Publisher<?> output = apply(extract(convertedEvent));
			return result(convertedEvent, output);
		}
		catch (Throwable ex) {
			if (context != null) {
				context.getLogger().throwing(getClass().getName(), "handle", ex);
			}
			if (ex instanceof RuntimeException) {
				throw (RuntimeException) ex;
			}
			if (ex instanceof Error) {
				throw (Error) ex;
			}
			throw new UndeclaredThrowableException(ex);
		}
		finally {
			if (context != null) {
				context.getLogger().fine("Handler processed a request.");
			}
		}
	}

	public void handleOutput(I input, OutputBinding<O> binding,
			ExecutionContext context) {
		try {
			if (context != null) {
				context.getLogger().fine("Handler processing a request.");
			}
			initialize(context);

			Object convertedEvent = convertEvent(input);
			Publisher<?> output = apply(extract(convertedEvent));
			binding.setValue(result(convertedEvent, output));

			if (context != null) {
				context.getLogger().fine("Handler processed a request.");
			}
		}
		catch (Throwable ex) {
			if (context != null) {
				context.getLogger().throwing(getClass().getName(), "handle", ex);
			}
			if (ex instanceof RuntimeException) {
				throw (RuntimeException) ex;
			}
			if (ex instanceof Error) {
				throw (Error) ex;
			}
			throw new UndeclaredThrowableException(ex);
		}
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

	private O result(Object input, Publisher<?> output) {
		List<Object> result = new ArrayList<>();
		for (Object value : Flux.from(output).toIterable()) {
			result.add(convertOutput(value));
		}
		if (isSingleValue(input) && result.size() == 1) {
			@SuppressWarnings("unchecked")
			O value = (O) result.get(0);
			return value;
		}
		@SuppressWarnings("unchecked")
		O value = (O) result;
		return value;
	}

	private boolean isSingleValue(Object input) {
		return !(input instanceof Collection);
	}

	@SuppressWarnings("unchecked")
	protected O convertOutput(Object output) {
		return (O) output;
	}
}
