/*
 * Copyright 2020-2021 the original author or authors.
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

package org.springframework.cloud.function.rsocket;

import io.rsocket.broker.client.spring.BrokerClientAutoConfiguration;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.rsocket.RSocketConnectorConfigurer;

/**
 * Configuration for components required to support RSocket Routing Client
 * integration with spring-cloud-function.
 *
 * @author Spencer Gibb
 * @since 3.1
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(BrokerClientAutoConfiguration.class)
@ConditionalOnProperty(name = FunctionProperties.PREFIX + ".rsocket.enabled", matchIfMissing = true)
@AutoConfigureBefore(BrokerClientAutoConfiguration.class)
@AutoConfigureAfter(RSocketAutoConfiguration.class)
class RSocketRoutingAutoConfiguration {

	@Bean
	RSocketConnectorConfigurer functionRSocketConnectorConfigurer(
			FunctionRSocketMessageHandler handler) {
		return connector -> connector.acceptor(handler.responder());
	}

}
