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

import java.lang.reflect.Type;
import java.util.function.Supplier;

import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.web.source.FunctionExporterAutoConfiguration.SourceActiveCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Dave Syer
 *
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(WebClient.class)
@Conditional(SourceActiveCondition.class)
@EnableConfigurationProperties(ExporterProperties.class)
public class FunctionExporterAutoConfiguration {

	private ExporterProperties props;

	@Autowired
	FunctionExporterAutoConfiguration(ExporterProperties props) {
		this.props = props;
	}

	@Bean
	@ConditionalOnProperty(prefix = "spring.cloud.function.web.export.sink", name = "url")
	public SupplierExporter sourceForwarder(RequestBuilder requestBuilder, DestinationResolver destinationResolver,
			FunctionCatalog catalog, WebClient.Builder builder) {
		return new SupplierExporter(requestBuilder, destinationResolver, catalog, builder.build(), this.props);
	}

	@Bean
	@ConditionalOnProperty(prefix = "spring.cloud.function.web.export.source", name = "url")
	public FunctionRegistration<Supplier<Flux<?>>> origin(WebClient.Builder builder) {
		HttpSupplier supplier = new HttpSupplier(builder.build(), this.props);
		FunctionRegistration<Supplier<Flux<?>>> registration = new FunctionRegistration<>(supplier);
		Type rawType = ResolvableType.forClassWithGenerics(Supplier.class, this.props.getSource().getType()).getType();
//		FunctionType functionType = FunctionType.supplier(this.props.getSource().getType()).wrap(Flux.class);
		FunctionType type = FunctionType.of(rawType);
		if (this.props.getSource().isIncludeHeaders()) {
//			type = type.message();
		}
		registration = registration.type(type);
		return registration;
	}

	@Bean
	public RequestBuilder simpleRequestBuilder(Environment environment) {
		SimpleRequestBuilder builder = new SimpleRequestBuilder(environment);
		if (this.props.getSink().getUrl() != null) {
			builder.setTemplateUrl(this.props.getSink().getUrl());
		}
		builder.setHeaders(this.props.getSink().getHeaders());
		return builder;
	}

	@Bean
	@ConditionalOnMissingBean
	public DestinationResolver simpleDestinationResolver() {
		return new SimpleDestinationResolver();
	}

	static class SourceActiveCondition extends AnyNestedCondition {

		SourceActiveCondition() {
			super(ConfigurationPhase.PARSE_CONFIGURATION);
		}

		@ConditionalOnNotWebApplication
		static class OnNotWebapp {

		}

		@ConditionalOnProperty(prefix = "spring.cloud.function.web.export", name = "enabled", matchIfMissing = true)
		static class Enabled {

		}

	}

}
