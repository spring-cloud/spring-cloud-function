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

package org.springframework.cloud.function.adapter.azure.injector;

import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.spi.inject.FunctionInstanceInjector;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.cloud.function.adapter.azure.AzureFunctionInstanceInjector;
import org.springframework.cloud.function.adapter.azure.AzureFunctionUtil;
import org.springframework.cloud.function.adapter.azure.helper.HttpRequestMessageStub;
import org.springframework.cloud.function.adapter.azure.helper.TestExecutionContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * @author Christian Tzolov
 */
public class FunctionInstanceInjectorServiceLoadingTest {

	static ExecutionContext executionContext = new TestExecutionContext("hello");

	@Test
	public void testFunctionInjector() throws Exception {

		FunctionInstanceInjector injector = initializeFunctionInstanceInjector();
		Assertions.assertThat(injector).isNotNull();
		Assertions.assertThat(injector).isInstanceOf(AzureFunctionInstanceInjector.class);

		System.setProperty("MAIN_CLASS", MyMainConfig.class.getName());

		MyAzureTestFunction functionInstance = injector.getInstance(MyAzureTestFunction.class);

		HttpRequestMessageStub<Optional<String>> request = new HttpRequestMessageStub<Optional<String>>();

		request.setBody(Optional.of("test"));

		String result = functionInstance.execute(request, executionContext);

		Assertions.assertThat(result).isEqualTo("TEST");

		Assertions.assertThat(functionInstance).isNotNull();
		Assertions.assertThat(functionInstance).isInstanceOf(MyAzureTestFunction.class);
	}

	private static FunctionInstanceInjector initializeFunctionInstanceInjector() {
		FunctionInstanceInjector functionInstanceInjector = null;
		ClassLoader prevContextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Iterator<FunctionInstanceInjector> iterator = ServiceLoader.load(FunctionInstanceInjector.class).iterator();
			if (iterator.hasNext()) {
				functionInstanceInjector = iterator.next();
				if (iterator.hasNext()) {
					throw new RuntimeException(
							"Customer function app has multiple FunctionInstanceInjector implementations");
				}
			}
			else {
				throw new IllegalStateException("Failed to resolve the AzureFunctionInstanceInjector as java service!");
			}
		}
		finally {
			Thread.currentThread().setContextClassLoader(prevContextClassLoader);
		}
		return functionInstanceInjector;
	}

	@Configuration
	@ComponentScan(excludeFilters = { @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)})
	public static class MyMainConfig {

		@Bean
		public Function<Message<String>, String> uppercase() {
			return message -> {
				ExecutionContext context = (ExecutionContext) message.getHeaders()
						.get(AzureFunctionUtil.EXECUTION_CONTEXT);
				Assertions.assertThat(context).isNotNull();
				Assertions.assertThat(context.getFunctionName()).isEqualTo("hello");
				return message.getPayload().toUpperCase(Locale.ROOT);
			};
		}
	}

	@Component
	public static class MyAzureTestFunction {

		@Autowired
		private Function<Message<String>, String> uppercase;

		@FunctionName("ditest")
		public String execute(
				@HttpTrigger(name = "req", methods = { HttpMethod.GET,
						HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
				ExecutionContext context) {

			Message<String> enhancedRequest = (Message<String>) AzureFunctionUtil.enhanceInputIfNecessary(
					request.getBody().get(),
					context);
			return uppercase.apply(enhancedRequest);
		}
	}
}
