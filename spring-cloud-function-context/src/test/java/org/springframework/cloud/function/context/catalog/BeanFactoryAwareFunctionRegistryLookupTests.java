/*
 * Copyright 2026-present the original author or authors.
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

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.config.JsonMessageConverter;
import org.springframework.cloud.function.json.GsonMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.messaging.converter.CompositeMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for lookup-related behavior of {@link BeanFactoryAwareFunctionRegistry}.
 */
public class BeanFactoryAwareFunctionRegistryLookupTests {

	private FunctionCatalog configureCatalog() {
		ApplicationContext context = new SpringApplicationBuilder(TestConfiguration.class).run();
		return context.getBean(FunctionCatalog.class);
	}

	@Test
	public void biFunctionWrapperExposesBiFunctionOutputType() {
		FunctionInvocationWrapper function = this.configureCatalog().lookup("length");
		assertThat(function.getOutputType()).isEqualTo(Integer.class);
	}

	@Test
	public void pojoFunctionExceptionIsNotWrapped() {
		ApplicationContext context = new SpringApplicationBuilder(PojoConfiguration.class).run();
		FunctionInvocationWrapper function = context.getBean(FunctionCatalog.class).lookup("failingPojo");
		assertThatThrownBy(() -> function.apply("hello"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("boom");
	}

	@Test
	public void acceptsNonGenericApplicationContext() {
		GsonMapper jsonMapper = new GsonMapper(new Gson());
		BeanFactoryAwareFunctionRegistry registry = new BeanFactoryAwareFunctionRegistry(
				new DefaultConversionService(),
				new CompositeMessageConverter(List.of(new JsonMessageConverter(jsonMapper))), jsonMapper, null, null);
		try (ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[0], false)) {
			assertThatCode(() -> registry.setApplicationContext(context)).doesNotThrowAnyException();
		}
	}

	@Configuration
	@EnableAutoConfiguration
	public static class TestConfiguration {

		@Bean
		public BiFunction<String, Map<String, Object>, Integer> length() {
			return (payload, headers) -> payload.length();
		}

	}

	@Configuration
	@EnableAutoConfiguration
	public static class PojoConfiguration {

		@Bean
		public FailingPojo failingPojo() {
			return new FailingPojo();
		}
	}

	public static class FailingPojo {

		public String fail(String value) {
			throw new IllegalStateException("boom");
		}
	}

}
