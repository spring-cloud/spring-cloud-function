/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.cloud.function.web.source;

import java.util.function.Supplier;

import org.springframework.boot.web.reactive.context.ConfigurableReactiveWebEnvironment;
import org.springframework.boot.web.reactive.context.ReactiveWebApplicationContext;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.ConfigurableWebEnvironment;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;

/**
 * @author Dave Syer
 * @since 2.0
 *
 */
class FunctionExporterInitializer implements ApplicationContextInitializer<GenericApplicationContext> {

	@Override
	public void initialize(GenericApplicationContext context) {
		if (ContextFunctionCatalogInitializer.enabled
				&& context.getEnvironment().getProperty("spring.functional.enabled", Boolean.class, false)
				&& isExporting(context)) {
			registerWebClient(context);
			registerExport(context);
		}
	}

	private void registerWebClient(GenericApplicationContext context) {
		if (ClassUtils.isPresent("org.springframework.web.reactive.function.client.WebClient",
				getClass().getClassLoader())) {
			if (context.getBeanFactory().getBeanNamesForType(WebClient.Builder.class, false, false).length == 0) {
				context.registerBean(WebClient.Builder.class, new Supplier<WebClient.Builder>() {
					@Override
					public Builder get() {
						return WebClient.builder();
					}
				});
			}
		}
	}

	private boolean isExporting(GenericApplicationContext context) {
		Boolean enabled = context.getEnvironment().getProperty("spring.cloud.function.web.export.enabled",
				Boolean.class);
		if (enabled != null) {
			return enabled;
		}
		if (ClassUtils.isPresent("org.springframework.web.context.WebApplicationContext",
				getClass().getClassLoader())) {
			if (context instanceof WebApplicationContext || context instanceof ReactiveWebApplicationContext
					|| context.getEnvironment() instanceof ConfigurableWebEnvironment
					|| context.getEnvironment() instanceof ConfigurableReactiveWebEnvironment) {
				return false;
			}
		}
		return true;
	}

	private void registerExport(GenericApplicationContext context) {
		context.registerBean(ExporterProperties.class, () -> new ExporterProperties());
		context.registerBean(FunctionExporterAutoConfiguration.class,
				() -> new FunctionExporterAutoConfiguration(context.getBean(ExporterProperties.class)));
		if (context.getBeanFactory().getBeanNamesForType(DestinationResolver.class, false, false).length == 0) {
			context.registerBean(DestinationResolver.class,
					() -> context.getBean(FunctionExporterAutoConfiguration.class).simpleDestinationResolver());
		}
		if (context.getBeanFactory().getBeanNamesForType(RequestBuilder.class, false, false).length == 0) {
			context.registerBean(RequestBuilder.class, () -> context.getBean(FunctionExporterAutoConfiguration.class)
					.simpleRequestBuilder(context.getEnvironment()));
		}
		if (context.getEnvironment().getProperty("spring.cloud.function.web.export.source.url") != null) {
			context.registerBean("origin", FunctionRegistration.class, () -> context
					.getBean(FunctionExporterAutoConfiguration.class).origin(context.getBean(WebClient.Builder.class)));
		}
		if (context.getEnvironment().getProperty("spring.cloud.function.web.export.sink.url") != null) {
			context.registerBean(SupplierExporter.class,
					() -> context.getBean(FunctionExporterAutoConfiguration.class).sourceForwarder(
							context.getBean(RequestBuilder.class), context.getBean(DestinationResolver.class),
							context.getBean(FunctionCatalog.class), context.getBean(WebClient.Builder.class)));
		}
	}

}
