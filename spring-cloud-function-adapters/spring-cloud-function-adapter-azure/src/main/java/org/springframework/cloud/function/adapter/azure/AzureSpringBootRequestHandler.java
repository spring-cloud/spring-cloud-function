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

import java.util.Collection;
import java.util.function.Function;
import java.util.logging.Logger;

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

	@SuppressWarnings("rawtypes")
	private static AzureSpringBootRequestHandler thisInitializer;

	private String functionName;

	private final static ExecutionContextDelegate EXECUTION_CTX_DELEGATE = new ExecutionContextDelegate();

	public AzureSpringBootRequestHandler(Class<?> configurationClass) {
		super(configurationClass);
	}

	public AzureSpringBootRequestHandler() {
		super();
	}

	public O handleRequest(ExecutionContext context) {
		return this.handleRequest(null, context);
	}

	@Override
	public void close() {
		thisInitializer = null;
		super.close();
	}

	@SuppressWarnings("unchecked")
	public O handleRequest(I input, ExecutionContext context) {
		EXECUTION_CTX_DELEGATE.targetContext = context;
		String name = "";
		try {
			if (context != null) {
				name = context.getFunctionName();
				context.getLogger().info("Handler processing a request for: " + name);
			}

			/*
			 * We need this "caching" logic to ensure that we don't reinitialize Spring Boot app on each invocation
			 * since Azure creates a new instance of this handler for each invocation,
			 * see https://github.com/spring-cloud/spring-cloud-function/issues/425
			 */
			if (thisInitializer == null || !thisInitializer.functionName.equals(name)) {
				initialize(EXECUTION_CTX_DELEGATE);
				this.functionName = name;
				thisInitializer = this;
				return (O) thisInitializer.handleRequest(input, context);
			}
			else {
				Publisher<?> events = input == null ? Mono.empty() : extract(convertEvent(input));
				Publisher<?> output = thisInitializer.apply(events);
				O result = result(input, output);
				if (context != null) {
					context.getLogger().fine("Handler processed a request for: " + name);
				}
				return result;
			}
		}
		catch (Throwable ex) {
			if (context != null) {
				context.getLogger().throwing(getClass().getName(), "handle", ex);
			}
			throw (RuntimeException) ex;
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

	private static class ExecutionContextDelegate implements ExecutionContext {

		ExecutionContext targetContext;

		@Override
		public Logger getLogger() {
			if (targetContext == null || targetContext.getLogger() == null) {
				return Logger.getAnonymousLogger();
			}
			return targetContext.getLogger();
		}

		@Override
		public String getInvocationId() {
			return targetContext.getInvocationId();
		}

		@Override
		public String getFunctionName() {
			return targetContext.getFunctionName();
		}

		@Override
		public String toString() {
			return "ExecutionContextDelegate over: " + this.targetContext;
		}
	}
}
