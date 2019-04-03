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

package org.springframework.cloud.function.adapter.azure;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collection;
import java.util.function.Function;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.context.AbstractSpringFunctionAdapterInitializer;

/**
 * @param <I> input type
 * @param <O> result type
 * @author Soby Chacko
 * @author Oleg Zhurakousky
 */
public class AzureSpringBootRequestHandler<I, O> extends AbstractSpringFunctionAdapterInitializer<ExecutionContext> {

	public AzureSpringBootRequestHandler(Class<?> configurationClass) {
		super(configurationClass);
	}

	public AzureSpringBootRequestHandler() {
		super();
	}

	public O handleRequest(ExecutionContext context) {
		return this.handleRequest(null, context);
	}

	public O handleRequest(I input, ExecutionContext context) {
		String name = null;
		try {
			if (context != null) {
				name = context.getFunctionName();
				context.getLogger().info("Handler processing a request for: " + name);
			}
			initialize(context);

			Publisher<?> events = input == null ? Mono.empty() : extract(convertEvent(input));

			Publisher<?> output = apply(events);
			return result(input, output);
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
				context.getLogger().fine("Handler processed a request for: " + name);
			}
		}
	}

	@Override
	protected String doResolveName(Object targetContext) {
		return ((ExecutionContext) targetContext).getFunctionName();
	}

	public void handleOutput(I input, OutputBinding<O> binding,
			ExecutionContext context) {
		O result = handleRequest(input, context);
		binding.setValue(result);
	}

	protected Object convertEvent(I input) {
		return input;
	}

	protected Flux<?> extract(Object input) {
		if (!isSingleInput(this.getFunction(), input)) {
			return Flux.fromIterable((Iterable<?>) input);
		}
		return Flux.just(input);
	}

	@SuppressWarnings("unchecked")
	protected O convertOutput(Object output) {
		return (O) output;
	}

	protected boolean isSingleInput(Function<?, ?> function, Object input) {
		if (!(input instanceof Collection)) {
			return true;
		}
		if (getInspector() != null) {
			return Collection.class
					.isAssignableFrom(getInspector().getInputType(function));
		}
		return ((Collection<?>) input).size() <= 1;
	}

	protected boolean isSingleOutput(Function<?, ?> function, Object output) {
		if (!(output instanceof Collection)) {
			return true;
		}
		if (getInspector() != null) {
			return Collection.class
					.isAssignableFrom(getInspector().getOutputType(function));
		}
		return ((Collection<?>) output).size() <= 1;
	}

}
