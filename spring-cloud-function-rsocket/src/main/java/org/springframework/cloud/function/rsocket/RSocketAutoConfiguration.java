/*
 * Copyright 2020-2020 the original author or authors.
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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.rsocket.RSocketMessageHandlerCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.util.StringUtils;

/**
 * Main configuration class for components required to support RSocket integration with
 * spring-cloud-function.
 *
 * @author Oleg Zhurakousky
 * @author Artem Bilan
 *
 * @since 3.1
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ FunctionProperties.class, RSocketFunctionProperties.class })
@ConditionalOnProperty(name = FunctionProperties.PREFIX + ".rsocket.enabled", matchIfMissing = true)
class RSocketAutoConfiguration implements ApplicationContextAware {

	private ApplicationContext applicationContext;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Bean
	@ConditionalOnMissingBean
	@Primary
	public FunctionRSocketMessageHandler functionRSocketMessageHandler(RSocketStrategies rSocketStrategies,
		ObjectProvider<RSocketMessageHandlerCustomizer> customizers, FunctionCatalog functionCatalog,
		FunctionProperties functionProperties) {

		FunctionRSocketMessageHandler rsocketMessageHandler = new FunctionRSocketMessageHandler(functionCatalog);
		rsocketMessageHandler.setRSocketStrategies(rSocketStrategies);
		customizers.orderedStream().forEach((customizer) -> customizer.customize(rsocketMessageHandler));
		registerFunctionsWithRSocketHandler(rsocketMessageHandler, functionCatalog, functionProperties);
		return rsocketMessageHandler;
	}

	private void registerFunctionsWithRSocketHandler(FunctionRSocketMessageHandler rsocketMessageHandler,
			FunctionCatalog functionCatalog, FunctionProperties functionProperties) {
		String definition = functionProperties.getDefinition();
		if (StringUtils.hasText(definition)) {
			FunctionInvocationWrapper function = FunctionRSocketUtils
					.registerFunctionForDestination(definition, functionCatalog, this.applicationContext);
			rsocketMessageHandler.registerFunctionHandler(new RSocketListenerFunction(function), definition);
			rsocketMessageHandler.registerFunctionHandler(new RSocketListenerFunction(function), "");
		}
	}

}
