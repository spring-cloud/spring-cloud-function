/*
 * Copyright 2021-present the original author or authors.
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

package org.springframework.cloud.function.actuator;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Oleg Zhurakousky
 */

public class FunctionsEndpointTests {

	@Test
	public void ensureIneligibleFunctionWontCauseNPE() {
		ApplicationContext context = new SpringApplicationBuilder(SampleConfiguration.class).run(
				"--spring.cloud.function.ineligible-definitions=echo,uppercase",
				"--spring.main.lazy-initialization=true");
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		FunctionsEndpoint endpoint = new FunctionsEndpoint(catalog);
		Map<String, Map<String, Object>> allFunctionsinCatalog = endpoint.listAll();
		// implicit assertion - no NPE
		assertThat(allFunctionsinCatalog.size()).isEqualTo(2);
		assertThat(allFunctionsinCatalog.containsKey("functionRouter"));
		assertThat(allFunctionsinCatalog.containsKey("reverse"));
	}

	@EnableAutoConfiguration
	@Configuration
	public static class SampleConfiguration {

		@Bean
		public Function<String, String> echo() {
			return v -> v;
		}

		@Bean
		public Function<String, String> uppercase() {
			return v -> v.toUpperCase(Locale.ROOT);
		}

		@Bean
		public Function<String, String> reverse() {
			return v -> new StringBuilder(v).reverse().toString();
		}

	}

}
