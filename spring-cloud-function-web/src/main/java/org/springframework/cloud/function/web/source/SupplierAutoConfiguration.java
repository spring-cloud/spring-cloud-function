/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.cloud.function.web.source;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.web.source.SupplierAutoConfiguration.SourceActiveCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Dave Syer
 *
 */
@Configuration
@ConditionalOnClass(WebClient.class)
@Conditional(SourceActiveCondition.class)
@EnableConfigurationProperties(SupplierProperties.class)
@ConditionalOnProperty(prefix = "spring.cloud.function.web.supplier", name = "enabled", matchIfMissing = true)
class SupplierAutoConfiguration {

	@Bean
	public SupplierExporter sourceForwarder(RequestBuilder requestBuilder,
			DestinationResolver destinationResolver, FunctionCatalog catalog,
			WebClient.Builder builder, SupplierProperties props) {
		return new SupplierExporter(requestBuilder, destinationResolver, catalog,
				builder.build(), props);
	}

	@Bean
	public RequestBuilder simpleRequestBuilder(SupplierProperties props,
			Environment environment) {
		SimpleRequestBuilder builder = new SimpleRequestBuilder(environment);
		if (props.getTemplateUrl() != null) {
			builder.setTemplateUrl(props.getTemplateUrl());
		}
		builder.setHeaders(props.getHeaders());
		return builder;
	}

	@Bean
	@ConditionalOnMissingBean
	public DestinationResolver simpleDestinationResolver() {
		return new SimpleDestinationResolver();
	}

	static class SourceActiveCondition extends AnyNestedCondition {

		public SourceActiveCondition() {
			super(ConfigurationPhase.PARSE_CONFIGURATION);
		}

		@ConditionalOnNotWebApplication
		static class OnNotWebapp {
		}

		@ConditionalOnProperty(prefix = "spring.cloud.function.web.supplier", name = "enabled")
		static class Enabled {
		}
	}
}
