/*
 * Copyright 2012-2019 the original author or authors.
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

import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.web.source.SupplierAutoConfiguration.SourceActiveCondition;
import org.springframework.cloud.function.web.util.HeaderUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Dave Syer
 *
 */
@Configuration
@ConditionalOnClass(WebClient.class)
@Conditional(SourceActiveCondition.class)
@EnableConfigurationProperties(ExporterProperties.class)
@ConditionalOnProperty(prefix = "spring.cloud.function.web.export", name = "enabled", matchIfMissing = true)
class SupplierAutoConfiguration {

	private static Log logger = LogFactory.getLog(SupplierAutoConfiguration.class);

	private ExporterProperties props;

	@Autowired
	SupplierAutoConfiguration(ExporterProperties props) {
		this.props = props;
	}

	@Bean
	@ConditionalOnProperty(prefix = "spring.cloud.function.web.export.sink", name = "url")
	public SupplierExporter sourceForwarder(RequestBuilder requestBuilder,
			DestinationResolver destinationResolver, FunctionCatalog catalog,
			WebClient.Builder builder) {
		return new SupplierExporter(requestBuilder, destinationResolver, catalog,
				builder.build(), this.props);
	}

	@Bean
	@ConditionalOnProperty(prefix = "spring.cloud.function.web.export.source", name = "url")
	public Supplier<Flux<?>> origin(WebClient.Builder builder) {
		WebClient client = builder.baseUrl(this.props.getSource().getUrl()).build();
		return () -> get(client);
	}

	private Flux<?> get(WebClient client) {
		Flux<?> result = client.get().exchange().flatMap(this::transform).repeat();
		if (this.props.isDebug()) {
			result = result.log();
		}
		return result.onErrorResume(TerminateException.class, error -> Mono.empty());
	}

	private Mono<?> transform(ClientResponse response) {
		HttpStatus status = response.statusCode();
		if (!status.is2xxSuccessful()) {
			if (this.props.isDebug()) {
				logger.info("Terminated origin Supplier with status="
						+ response.statusCode());
			}
			return Mono.error(TerminateException.INSTANCE);
		}
		return response.bodyToMono(this.props.getSource().getType())
				.map(value -> message(response, value));
	}

	private Object message(ClientResponse response, Object payload) {
		if (!this.props.getSource().isIncludeHeaders()) {
			return payload;
		}
		return MessageBuilder.withPayload(payload)
				.copyHeaders(HeaderUtils.fromHttp(
						HeaderUtils.sanitize(response.headers().asHttpHeaders())))
				.build();
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

	private static class TerminateException extends RuntimeException {

		static final TerminateException INSTANCE = new TerminateException();

		TerminateException() {
			super("Planned termination");
		}

		@Override
		public synchronized Throwable fillInStackTrace() {
			return this;
		}

	}

	static class SourceActiveCondition extends AnyNestedCondition {

		SourceActiveCondition() {
			super(ConfigurationPhase.PARSE_CONFIGURATION);
		}

		@ConditionalOnNotWebApplication
		static class OnNotWebapp {

		}

		@ConditionalOnProperty(prefix = "spring.cloud.function.web.export", name = "enabled")
		static class Enabled {

		}

	}

}
