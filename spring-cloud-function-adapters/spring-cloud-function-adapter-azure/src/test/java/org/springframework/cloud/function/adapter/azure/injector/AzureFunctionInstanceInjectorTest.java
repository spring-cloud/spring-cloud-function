/*
 * Copyright 2022-2022 the original author or authors.
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
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.logging.Logger;

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
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.cloud.function.adapter.azure.AzureFunctionInstanceInjector;
import org.springframework.cloud.function.adapter.azure.AzureFunctionUtil;
import org.springframework.cloud.function.adapter.azure.HttpFunctionInvokerTests;
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
public class AzureFunctionInstanceInjectorTest {

	static ExecutionContext executionContext = new ExecutionContext() {
		@Override
		public Logger getLogger() {
			return Logger.getLogger(AzureFunctionInstanceInjectorTest.class.getName());
		}

		@Override
		public String getInvocationId() {
			return "id1";
		}

		@Override
		public String getFunctionName() {
			return "hello";
		}
	};

	@Test
	public void testFunctionInjector() throws Exception {

		FunctionInstanceInjector injector = initializeFunctionInstanceInjector();
		Assertions.assertThat(injector).isNotNull();
		Assertions.assertThat(injector).isInstanceOf(AzureFunctionInstanceInjector.class);

		System.setProperty("MAIN_CLASS", MyMainConfig.class.getName());

		MyAzureTestFunction functionInstance = injector.getInstance(MyAzureTestFunction.class);

		HttpFunctionInvokerTests.HttpRequestMessageStub<Optional<String>> request = new HttpFunctionInvokerTests.HttpRequestMessageStub<Optional<String>>();

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
				functionInstanceInjector = new FunctionInstanceInjector() {
					@Override
					public <T> T getInstance(Class<T> functionClass) throws Exception {
						return functionClass.getDeclaredConstructor().newInstance();
					}
				};
			}
		}
		finally {
			Thread.currentThread().setContextClassLoader(prevContextClassLoader);
		}
		return functionInstanceInjector;
	}

	@Configuration
	@ComponentScan(excludeFilters = { @Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
			@Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class) })
	public static class MyMainConfig {

		@Bean
		public Function<Message<String>, String> uppercase() {
			return message -> {
				ExecutionContext context = (ExecutionContext) message.getHeaders()
						.get(AzureFunctionUtil.EXECUTION_CONTEXT);
				Assertions.assertThat(context).isNotNull();
				Assertions.assertThat(context.getFunctionName()).isEqualTo("hello");
				return message.getPayload().toUpperCase();
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
