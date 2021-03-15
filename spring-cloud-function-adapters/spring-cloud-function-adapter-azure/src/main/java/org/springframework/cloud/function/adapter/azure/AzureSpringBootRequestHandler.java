/*
 * Copyright 2017-2021 the original author or authors.
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
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.OutputBinding;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.context.AbstractSpringFunctionAdapterInitializer;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * @param <I> input type
 * @param <O> result type
 * @author Soby Chacko
 * @author Oleg Zhurakousky
 *
 * @deprecated since 3.2 in favor of {@link FunctionInvoker}
 */
@Deprecated
public class AzureSpringBootRequestHandler<I, O> extends AbstractSpringFunctionAdapterInitializer<ExecutionContext> {

	@SuppressWarnings("rawtypes")
	private static AzureSpringBootRequestHandler thisInitializer;

	private static FunctionCatalog functionCatalog;

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
			if (thisInitializer == null /*|| !thisInitializer.functionName.equals(name)*/) {
				initialize(EXECUTION_CTX_DELEGATE);
				functionCatalog = this.catalog;
				thisInitializer = this;
				return (O) thisInitializer.handleRequest(input, context);
			}
			else {
				this.catalog = functionCatalog;
				thisInitializer.clear(name);
				Publisher<?> events = input == null ? Mono.empty() : extract(convertEvent(input));
				if (events instanceof Flux) {
					events = Flux.from(events).map(v -> this.toMessage(v, context));
				}
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
			throw new RuntimeException(ex);
		}
	}

	public void handleOutput(I input, OutputBinding<O> binding,
			ExecutionContext context) {
		O result = handleRequest(input, context);
		binding.setValue(result);
	}

	@Override
	protected String doResolveName(Object targetContext) {
		return ((ExecutionContext) targetContext).getFunctionName();
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
		if (function != null) {
			return Collection.class
					.isAssignableFrom(((FunctionInvocationWrapper) function).getRawInputType());
		}
		return ((Collection<?>) input).size() <= 1;
	}

	protected boolean isSingleOutput(Function<?, ?> function, Object output) {
		if (!(output instanceof Collection)) {
			return true;
		}
		if (function != null) {
			Class<?> outputType = FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(((FunctionInvocationWrapper) function).getOutputType()));
			return Collection.class.isAssignableFrom(outputType);
		}
		return ((Collection<?>) output).size() <= 1;
	}

	@SuppressWarnings("rawtypes")
	private Message<?> toMessage(Object value, ExecutionContext context) {
		if (value instanceof Message) {
			return (Message<?>) value;
		}
		else {
			Object payload = value;
			if (value instanceof HttpRequestMessage) {
				payload = ((HttpRequestMessage) value).getBody();
				if (payload == null) {
					payload = ((HttpRequestMessage) value).getQueryParameters();
				}
			}
			return MessageBuilder.withPayload(payload)
					.setHeader(AbstractSpringFunctionAdapterInitializer.TARGET_EXECUTION_CTX_NAME, context).build();
		}
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
