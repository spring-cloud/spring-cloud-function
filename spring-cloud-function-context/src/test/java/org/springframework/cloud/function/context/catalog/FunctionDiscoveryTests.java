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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.adapter.ForwardedHeaderTransformer;

import static org.assertj.core.api.Assertions.assertThat;

class FunctionDiscoveryTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ContextFunctionCatalogAutoConfiguration.class));

	@Test
	void forwardedHeaderTransformerIsNotDiscoveredAsDefaultFunction() {
		this.contextRunner.withBean("forwardedHeaderTransformer", ForwardedHeaderTransformer.class).run(context -> {
			FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
			assertThat(catalog.getNames(null)).doesNotContain("forwardedHeaderTransformer");
			assertThat(catalog.getNames(Function.class)).doesNotContain("forwardedHeaderTransformer");
			assertThat((Object) catalog.lookup("")).isNull();
			assertThat((Object) catalog.lookup("forwardedHeaderTransformer")).isNull();
		});
	}

	@Test
	void consumerIsDiscoveredAlongsideRenamedForwardedHeaderTransformer() {
		this.contextRunner.withBean("customTransformer", ForwardedHeaderTransformer.class)
				.withUserConfiguration(ConsumerConfiguration.class).run(context -> {
					FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
					assertThat(catalog.getNames(null)).doesNotContain("customTransformer");
					assertThat((Object) catalog.lookup("customTransformer")).isNull();
					assertThat((Object) catalog.lookup("customTransformer|myConsumer")).isNull();
					FunctionInvocationWrapper function = catalog.lookup("");
					assertThat(function).isNotNull();
					assertThat(function.getFunctionDefinition()).isEqualTo("myConsumer");
					function.apply("hello");
					assertThat(context.getBean(ConsumerConfiguration.class).received).hasValue("hello");
				});
	}

	@Test
	void forwardedHeaderTransformerStillProcessesHttpRequests() {
		this.contextRunner.withBean(ForwardedHeaderTransformer.class).run(context -> {
			context.getBean(FunctionCatalog.class).getNames(null);
			ForwardedHeaderTransformer transformer = context.getBean(ForwardedHeaderTransformer.class);
			MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/path")
					.header("Forwarded", "proto=https;host=example.org").build();
			assertThat(transformer.apply(request).getURI().toString()).isEqualTo("https://example.org/path");
		});
	}

	@Test
	void discoveryDoesNotInstantiateLazyTransformer() {
		AtomicBoolean instantiated = new AtomicBoolean();
		this.contextRunner.withBean("transformer", ForwardedHeaderTransformer.class, () -> {
			instantiated.set(true);
			return new ForwardedHeaderTransformer();
		}, definition -> definition.setLazyInit(true)).run(context -> {
			FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
			assertThat(catalog.getNames(null)).doesNotContain("transformer");
			assertThat((Object) catalog.lookup("")).isNull();
			assertThat(instantiated).isFalse();
		});
	}

	@Test
	void excludedBeanNameDoesNotPreventConsumerDiscovery() {
		this.contextRunner.withUserConfiguration(ConsumerConfiguration.class, OtherFunctionConfiguration.class)
				.withPropertyValues("spring.cloud.function.ineligible-definitions=otherFunction").run(context -> {
					FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
					assertThat(catalog.getNames(null)).doesNotContain("otherFunction");
					assertThat((Object) catalog.lookup("otherFunction")).isNull();
					FunctionInvocationWrapper function = catalog.lookup("");
					assertThat(function).isNotNull();
					assertThat(function.getFunctionDefinition()).isEqualTo("myConsumer");
				});
	}

	@Test
	void excludedBeanTypeDoesNotPreventConsumerDiscovery() {
		this.contextRunner.withUserConfiguration(ConsumerConfiguration.class)
				.withBean("otherFunction", OtherFunction.class)
				.withPropertyValues("spring.cloud.function.ineligible-definitions=" + OtherFunction.class.getName())
				.run(context -> {
					FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
					assertThat(catalog.getNames(null)).doesNotContain("otherFunction");
					FunctionInvocationWrapper function = catalog.lookup("");
					assertThat(function).isNotNull();
					assertThat(function.getFunctionDefinition()).isEqualTo("myConsumer");
				});
	}

	@Test
	void explicitDefinitionStillSelectsFunctionAmongMultipleCandidates() {
		this.contextRunner.withBean(ForwardedHeaderTransformer.class)
				.withUserConfiguration(ConsumerConfiguration.class, OtherFunctionConfiguration.class)
				.withPropertyValues("spring.cloud.function.definition=otherFunction").run(context -> {
					FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
					Function<String, String> function = catalog.lookup("");
					assertThat(function).isNotNull();
					assertThat(function.apply("hello")).isEqualTo("hello");
				});
	}

	@Test
	void forwardedHeaderTransformerCannotBeRegisteredDirectly() {
		this.contextRunner.run(context -> {
			FunctionRegistry registry = context.getBean(FunctionRegistry.class);
			registry.register(new FunctionRegistration<>(new ForwardedHeaderTransformer(), "transformer"));
			assertThat(registry.getNames(null)).doesNotContain("transformer");
			assertThat((Object) registry.lookup("")).isNull();
		});
	}

	@Test
	void multipleEligibleFunctionsStillRequireExplicitSelection() {
		this.contextRunner.withBean(ForwardedHeaderTransformer.class)
				.withUserConfiguration(ConsumerConfiguration.class, OtherFunctionConfiguration.class).run(context -> {
					FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
					assertThat((Object) catalog.lookup("")).isNull();
					assertThat(catalog.getNames(null)).contains("myConsumer", "otherFunction");
				});
	}

	@Test
	void ordinarySingleConsumerDiscoveryIsUnchanged() {
		this.contextRunner.withUserConfiguration(ConsumerConfiguration.class).run(context -> {
			FunctionInvocationWrapper function = context.getBean(FunctionCatalog.class).lookup("");
			assertThat(function).isNotNull();
			assertThat(function.getFunctionDefinition()).isEqualTo("myConsumer");
			function.apply("hello");
			assertThat(context.getBean(ConsumerConfiguration.class).received).hasValue("hello");
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class ConsumerConfiguration {

		private final AtomicReference<String> received = new AtomicReference<>();

		@Bean
		Consumer<String> myConsumer() {
			return this.received::set;
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class OtherFunctionConfiguration {

		@Bean
		Function<String, String> otherFunction() {
			return Function.identity();
		}

	}

	static class OtherFunction implements Function<String, String> {

		@Override
		public String apply(String value) {
			return value;
		}

	}

}
