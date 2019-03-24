/*
 * Copyright 2019-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.context;

import java.util.function.Function;

import org.junit.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 * @author Semyon Fishman
 *
 */
public class HybridFunctionalRegistrationTests {

	// see https://github.com/spring-cloud/spring-cloud-function/issues/258
	@Test
	public void testNoDoubleRegistrationInHybridMode() {
		ConfigurableApplicationContext context = FunctionalSpringApplication
				.run(UppercaseFunction.class, "--spring.functional.enabled=false");
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);

		assertThat(context.containsBean("function")).isTrue();
		assertThat(catalog.size()).isEqualTo(1);
	}

	@SpringBootConfiguration
	@ImportAutoConfiguration({
		ContextFunctionCatalogAutoConfiguration.class,
		JacksonAutoConfiguration.class }
	)
	public static class UppercaseFunction implements Function<String, String> {

		@Override
		public String apply(String t) {
			return t.toUpperCase();
		}
	}

}
