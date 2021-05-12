/*
 * Copyright 2019-2021 the original author or authors.
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

package org.springframework.cloud.function.context;

import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionProperties.FunctionConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionPropertiesTests {

	@Test
	public void testInputHeaderMappingPropertyWithIndex() throws Exception {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
				SampleFunctionConfiguration.class).web(WebApplicationType.NONE).run(
						"--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true",
						"--spring.cloud.function.definition=echo",
						"--spring.cloud.function.configuration.echo.input-header-mapping-expression[0].key1=hello1",
						"--spring.cloud.function.configuration.echo.input-header-mapping-expression[0].key2=hello2",
						"--spring.cloud.function.configuration.echo.input-header-mapping-expression[1].key12=hello12")) {
			FunctionProperties functionProperties = context
					.getBean(FunctionProperties.class);
			FunctionConfigurationProperties configuration = functionProperties
					.getConfiguration().get("echo");
			assertThat(configuration.getInputHeaderMappingExpression()).containsKey("0");
			assertThat(configuration.getInputHeaderMappingExpression()).containsKey("1");
		}
	}

	@Test
	public void testInputHeaderMappingPropertyWithoutIndex() throws Exception {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
				SampleFunctionConfiguration.class).web(WebApplicationType.NONE).run(
						"--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true",
						"--spring.cloud.function.definition=echo",
						"--spring.cloud.function.configuration.echo.input-header-mapping-expression.key1=hello1",
						"--spring.cloud.function.configuration.echo.input-header-mapping-expression.key2=hello2")) {
			FunctionProperties functionProperties = context
					.getBean(FunctionProperties.class);
			FunctionConfigurationProperties configuration = functionProperties
					.getConfiguration().get("echo");
			assertThat(configuration.getInputHeaderMappingExpression()).containsKey("0");
		}
	}

	@Test
	public void testInputHeaderMappingPropertyWithCompositionWithIndex() throws Exception {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
				SampleFunctionConfiguration.class).web(WebApplicationType.NONE).run(
						"--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true",
//						"--spring.cloud.function.definition=echo|foo",
						"--spring.cloud.function.configuration.echofoo.input-header-mapping-expression[0].key1=hello1",
						"--spring.cloud.function.configuration.echofoo.input-header-mapping-expression[0].key2=hello2",
						"--spring.cloud.function.configuration.echofoo.input-header-mapping-expression[1].key12=hello12")) {
			FunctionProperties functionProperties = context
					.getBean(FunctionProperties.class);
			FunctionConfigurationProperties configuration = functionProperties
					.getConfiguration().get("echofoo");
			Map<Object, Object> keyValueExpression = (Map<Object, Object>) configuration.getInputHeaderMappingExpression().get("0");


//			FunctionCatalog functionCatalog = context.getBean(FunctionCatalog.class);
//			FunctionInvocationWrapper function = functionCatalog.lookup("echo|foo");
//			System.out.println(function.apply(new GenericMessage<String>("helo")));



//			System.out.println(keyValueExpression.get("key1"));
//			assertThat(configuration.getInputHeaderMappingExpression()).containsKey("0");
//			assertThat(configuration.getInputHeaderMappingExpression()).containsKey("1");
		}
	}

	@Test
	public void testInputHeaderMappingPropertyWithCompositionWithoutIndex() throws Exception {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
				SampleFunctionConfiguration.class).web(WebApplicationType.NONE).run(
						"--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true",
						"--spring.cloud.function.definition=echo|foo",
						"--spring.cloud.function.configuration.echofoo.input-header-mapping-expression.key1=hello1",
						"--spring.cloud.function.configuration.echofoo.input-header-mapping-expression.key2=hello2")) {
			FunctionProperties functionProperties = context
					.getBean(FunctionProperties.class);
			FunctionConfigurationProperties configuration = functionProperties
					.getConfiguration().get("echofoo");
			assertThat(configuration.getInputHeaderMappingExpression()).containsKey("0");
		}
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class SampleFunctionConfiguration {

		@Bean
		public Function<String, String> echo() {
			return x -> x;
		}

		@Bean
		public Function<String, String> foo() {
			return x -> x;
		}
	}
}
