/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.function.context.catalog;

import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Oleg Zhurakousky
 */
public class MarcinSleuthTests {

	private ApplicationContext context;

	private FunctionCatalog configureCatalog(Class<?>... configClass) {
		this.context = new SpringApplicationBuilder(configClass)
				.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true");
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		return catalog;
	}

	@BeforeEach
	public void before() {
		System.clearProperty("spring.cloud.function.definition");
	}

	@Test
	public void testMarcinHeaderInjection() {
		FunctionCatalog catalog = this.configureCatalog(SampleFunctionConfiguration.class);

		FunctionInvocationWrapper function = catalog.lookup("echo", "application/json");
		Message<byte[]>  result = (Message<byte[]>) function.apply(MessageBuilder.withPayload("hello").build());
		assertThat(result.getHeaders().get("his-name")).isEqualTo("marcin");
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class SampleFunctionConfiguration {

		@Bean
		public Function<Message<String>, Message<String>> echo() {
			return m -> m;
		}

		@Bean
		public FunctionAroundWrapper aroundWrapper() {
			return new FunctionAroundWrapper() {

				@Override
				protected Object doApply(Message<byte[]> input,
						FunctionInvocationWrapper targetFunction) {
					return targetFunction.apply(MessageBuilder.fromMessage(input).setHeader("his-name", "marcin").build());
				}
			};
		}
	}
}
