/*
 * Copyright 2022-present the original author or authors.
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

package example;

import java.util.List;
import java.util.Locale;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.adapter.azure.FunctionInvoker;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;

/**
 * Sample that shows how to customize the default function result handling by operating on the {@link Flux} returned
 * from the {@link Config#echoStream()} echoStream} function.
 *
 * @author Chris Bono
 */
public class ReactiveEchoCustomResultHandler extends FunctionInvoker<List<String>, String> {

	private static final Log logger = LogFactory.getLog(ReactiveEchoCustomResultHandler.class);

	@FunctionName("echoStream")
	public String execute(@HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
			HttpRequestMessage<List<String>> request, ExecutionContext context
	) {
		return handleRequest(request.getBody(), context);
	}

	@Override
	protected String postProcessFluxFunctionResult(List<String> rawInputs, Object functionInputs, Flux<?> functionResult,
		FunctionInvocationWrapper function, ExecutionContext executionContext
	) {
		functionResult
			.doFirst(() -> executionContext.getLogger().info("BEGIN echo post-processing work ..."))
			.mapNotNull((v) -> v.toString().toUpperCase(Locale.ROOT))
			.doFinally((signalType) -> executionContext.getLogger().info("END echo post-processing work"))
			.subscribe((v) -> executionContext.getLogger().info("   " + v));
		return "Kicked off job for " + rawInputs;
	}

}
