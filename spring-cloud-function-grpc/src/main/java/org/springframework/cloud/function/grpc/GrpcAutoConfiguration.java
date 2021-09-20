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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.2
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FunctionGrpcProperties.class)
@ConditionalOnProperty(name = "spring.cloud.function.grpc.mode", havingValue = "server", matchIfMissing = false)
public class GrpcAutoConfiguration {

	@Bean
	public GrpcServer grpcServer(FunctionGrpcProperties grpcProperties, GrpcMessagingServiceImpl grpcMessagingService) {
		return new GrpcServer(grpcProperties, grpcMessagingService);
	}


	@Bean
	public GrpcMessagingServiceImpl grpcMessageService(FunctionProperties funcProperties, FunctionCatalog functionCatalog) {
		return new GrpcMessagingServiceImpl(funcProperties, functionCatalog);
	}
}
