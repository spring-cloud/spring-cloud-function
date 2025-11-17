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

import java.util.Locale;
import java.util.Optional;
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
import org.springframework.cloud.function.adapter.azure.AzureFunctionInstanceInjector;
import org.springframework.cloud.function.adapter.azure.AzureFunctionUtil;
import org.springframework.cloud.function.adapter.azure.helper.HttpRequestMessageStub;
import org.springframework.cloud.function.adapter.azure.helper.TestExecutionContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * This is an example JUnit test for Azure Adapter.
 *
 * @author Christian Tzolov
 */
public class AzureFunctionInstanceInjectorTest {

	static ExecutionContext TEST_EXECUTION_CONTEXT = new TestExecutionContext("hello");

	@Test
	public void testFunctionInjector() throws Exception {

		// The SpringBootApplication class
		System.setProperty("MAIN_CLASS", MySpringConfig.class.getName());

		FunctionInstanceInjector injector = new AzureFunctionInstanceInjector();

		// Emulates the Azure Function Java Runtime DI call
		MyAzureFunction azureFunction = injector.getInstance(MyAzureFunction.class);

		Assertions.assertThat(azureFunction).isNotNull();

		HttpRequestMessageStub<Optional<String>> requestStub = new HttpRequestMessageStub<Optional<String>>();

		requestStub.setBody(Optional.of("payload"));

		String result = azureFunction.execute(requestStub, TEST_EXECUTION_CONTEXT);

		Assertions.assertThat(result).isEqualTo("PAYLOAD");

		Assertions.assertThat(azureFunction).isInstanceOf(MyAzureFunction.class);
	}

	@Component
	public static class MyAzureFunction {

		@Autowired
		private Function<Message<String>, String> uppercase;

		@FunctionName("hello")
		public String execute(
				@HttpTrigger(name = "req", methods = { HttpMethod.GET, HttpMethod.POST },
						authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
				ExecutionContext context) {

			Message<String> enhancedRequest = (Message<String>) AzureFunctionUtil
				.enhanceInputIfNecessary(request.getBody().get(), context);

			return uppercase.apply(enhancedRequest);
		}

	}

	@Configuration
	@ComponentScan
	public static class MySpringConfig {

		@Bean
		public Function<Message<String>, String> uppercaseBean() {
			return message -> {
				ExecutionContext context = (ExecutionContext) message.getHeaders()
					.get(AzureFunctionUtil.EXECUTION_CONTEXT);

				Assertions.assertThat(context).isNotNull();
				Assertions.assertThat(context.getFunctionName()).isEqualTo("hello");

				return message.getPayload().toUpperCase(Locale.ROOT);
			};

		}

	}

}
