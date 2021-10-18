/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.function.grpc;

import java.util.List;

import io.grpc.BindableService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.2
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FunctionGrpcProperties.class)
@ConditionalOnProperty(name = "spring.cloud.function.grpc.server", havingValue = "true", matchIfMissing = true)
class GrpcAutoConfiguration {

	@Bean
	public GrpcServer grpcServer(FunctionGrpcProperties grpcProperties, BindableService[] grpcMessagingServices) {
		Assert.notEmpty(grpcMessagingServices, "'grpcMessagingServices' must not be null or empty");
		if (StringUtils.hasText(grpcProperties.getServiceClassName())) {
			for (BindableService bindableService : grpcMessagingServices) {
				if (bindableService.getClass().getName().equals(grpcProperties.getServiceClassName())) {
					return new GrpcServer(grpcProperties, new BindableService[] {bindableService});
				}
			}
		}
		return new GrpcServer(grpcProperties, grpcMessagingServices);
	}


	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Bean
	public BindableService grpcSpringMessageHandler(MessageHandlingHelper helper) {
		return new GrpcServerMessageHandler(helper);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Bean
	public MessageHandlingHelper grpcMessageHandlingHelper(List<GrpcMessageConverter<?>> grpcConverters,
			FunctionProperties funcProperties, FunctionCatalog functionCatalog) {
		return new MessageHandlingHelper(grpcConverters, functionCatalog, funcProperties);
	}

	@Bean
	public GrpcSpringMessageConverter grpcSpringMessageConverter() {
		return new GrpcSpringMessageConverter();
	}
}
