/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.stream.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.converter.CompositeMessageConverterFactory;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

/**
 * @author Mark Fisher
 * @author Marius Bogoevici
 */
@EnableConfigurationProperties(StreamConfigurationProperties.class)
@ConditionalOnClass(Binder.class)
@ConditionalOnBean(FunctionCatalog.class)
@ConditionalOnProperty(name = "spring.cloud.stream.enabled", havingValue = "true", matchIfMissing = true)
@EnableBinding(Processor.class)
public class StreamAutoConfiguration {

	@Autowired
	private StreamConfigurationProperties properties;

	@Bean
	// Because of the underlying behaviour of Spring AMQP etc., sources do not start
	// up and fail gracefully if the broker is down. So we need a flag to be able to
	// switch this off and stop the app failing on startup.
	// TODO: find a slicker way to do it (e.g. backoff if the broker is down)
	@ConditionalOnProperty(name = "spring.cloud.function.stream.supplier.enabled", havingValue = "true", matchIfMissing = true)
	public SupplierInvokingMessageProducer<Object> supplierInvoker(
			FunctionCatalog registry) {
		return new SupplierInvokingMessageProducer<Object>(registry);
	}

	@Bean
	public StreamListeningFunctionInvoker functionInvoker(FunctionCatalog registry,
			FunctionInspector functionInspector,
			@Lazy CompositeMessageConverterFactory compositeMessageConverterFactory) {
		return new StreamListeningFunctionInvoker(registry, functionInspector,
				compositeMessageConverterFactory, properties.getDefaultRoute());
	}

}
