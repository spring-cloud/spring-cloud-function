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

package org.springframework.cloud.function.context.config;

import io.cloudevents.spring.messaging.CloudEventMessageConverter;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.converter.avro.AvroSchemaMessageConverter;
import org.springframework.cloud.function.context.converter.avro.AvroSchemaServiceManager;
import org.springframework.cloud.function.context.scan.TestFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests the conditional loading aspects of the {@link ContextFunctionCatalogAutoConfiguration}.
 *
 * @author Chris Bono
 */
public class ContextFunctionCatalogAutoConfigurationConditionalLoadingTests {

	protected final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ContextFunctionCatalogAutoConfiguration.class));

	@Test
	void autoConfigDisabledWhenCustomFunctionCatalogExists() {
		contextRunner.withBean(FunctionCatalog.class, () -> mock(FunctionCatalog.class))
			.run((context) -> assertThat(context).doesNotHaveBean(FunctionRegistry.class));
	}

	@Nested
	class AvroSchemaMessageConverterConfig {

		@Test
		void avroSchemaMessageConverterBeansLoadedWhenAvroOnClasspath() {
			contextRunner.run((context) -> assertThat(context).hasSingleBean(AvroSchemaServiceManager.class)
				.hasSingleBean(AvroSchemaMessageConverter.class));
		}

		@Test
		void avroSchemaMessageConverterBeansNotLoadedWhenAvroNotOnClasspath() {
			contextRunner.withClassLoader(new FilteredClassLoader(Schema.class)).run((context) ->
				assertThat(context).doesNotHaveBean(AvroSchemaServiceManager.class)
					.doesNotHaveBean(AvroSchemaMessageConverter.class));
		}

		@Test
		void customAvroSchemaServiceManagerIsRespected() {
			AvroSchemaServiceManager customManager = mock(AvroSchemaServiceManager.class);
			contextRunner.withBean(AvroSchemaServiceManager.class, () -> customManager)
				.run((context) -> assertThat(context).getBean(AvroSchemaServiceManager.class).isSameAs(customManager));
		}

		@Test
		void customAvroSchemaMessageConverterIsRespected() {
			AvroSchemaMessageConverter customConverter = mock(AvroSchemaMessageConverter.class);
			contextRunner.withBean(AvroSchemaMessageConverter.class, () -> customConverter)
				.run((context) -> assertThat(context).getBean(AvroSchemaMessageConverter.class).isSameAs(customConverter));
		}
	}

	@Nested
	class CloudEventsMessageConverterConfig {

		@Test
		void cloudEventsMessageConverterBeanLoadedWhenCloudEventsOnClasspath() {
			contextRunner.run((context) -> assertThat(context).hasSingleBean(CloudEventMessageConverter.class));
		}

		@Test
		void cloudEventsMessageConverterBeanNotLoadedWhenCloudEventsNotOnClasspath() {
			contextRunner.withClassLoader(new FilteredClassLoader(CloudEventMessageConverter.class)).run((context) ->
				assertThat(context).doesNotHaveBean(CloudEventMessageConverter.class));
		}

		@Test
		void customCloudEventsMessageConverterIsRespected() {
			CloudEventMessageConverter customConverter = mock(CloudEventMessageConverter.class);
			contextRunner.withBean(CloudEventMessageConverter.class, () -> customConverter)
				.run((context) -> assertThat(context).getBean(CloudEventMessageConverter.class).isSameAs(customConverter));
		}
	}

	@Nested
	class PlainFunctionScanConfig {

		@Test
		void functionScanConfigEnabledByDefault() {
			contextRunner.withPropertyValues("spring.cloud.function.scan.packages:" + TestFunction.class.getPackageName())
				.run((context) -> assertThat(context).hasSingleBean(TestFunction.class));
		}

		@Test
		void functionScanConfigEnabledWhenEnabledPropertySetToTrue() {
			contextRunner.withPropertyValues("spring.cloud.function.scan.packages:" + TestFunction.class.getPackageName(),
					"spring.cloud.function.scan.enabled:true")
				.run((context) -> assertThat(context).hasSingleBean(TestFunction.class));
		}

		@Test
		void functionScanConfigEnabledWithScanPackagesPointingToNoFunctions() {
			contextRunner.withPropertyValues("spring.cloud.function.scan.packages:" + TestFunction.class.getPackageName() + ".faux",
					"spring.cloud.function.scan.enabled:true")
				.run((context) -> assertThat(context).doesNotHaveBean(TestFunction.class));
		}

		@Test
		void functionScanConfigDisabledWhenEnabledPropertySetToFalse() {
			contextRunner.withPropertyValues("spring.cloud.function.scan.packages:" + TestFunction.class.getPackageName(),
					"spring.cloud.function.scan.enabled:false")
				.run((context) -> assertThat(context).doesNotHaveBean(TestFunction.class));
		}

	}
}
