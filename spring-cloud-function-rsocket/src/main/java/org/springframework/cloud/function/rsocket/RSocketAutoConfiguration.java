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

import java.net.URI;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.rsocket.RSocketMessageHandlerCustomizer;
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration;
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration;
import org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Main configuration class for components required to support RSocket integration with spring-cloud-function.
 *
 * @author Oleg Zhurakousky
 * @author Artem Bilan
 *
 * @since 3.1
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FunctionProperties.class)
@AutoConfigureAfter(RSocketRequesterAutoConfiguration.class)
@AutoConfigureBefore({ RSocketServerAutoConfiguration.class, RSocketMessagingAutoConfiguration.class })
public class RSocketAutoConfiguration implements ApplicationContextAware {

	private static final Log LOGGER = LogFactory.getLog(RSocketAutoConfiguration.class);

	private static final Pattern WS_URI_PATTERN = Pattern.compile("^(https?|wss?)://.+");

	private final FunctionCatalog functionCatalog;

	private final FunctionProperties functionProperties;

	private final RSocketRequester.Builder rsocketRequesterBuilder;

	private ApplicationContext applicationContext;

	public RSocketAutoConfiguration(FunctionCatalog functionCatalog, FunctionProperties functionProperties,
		RSocketRequester.Builder rsocketRequesterBuilder) {

		this.functionCatalog = functionCatalog;
		this.functionProperties = functionProperties;
		this.rsocketRequesterBuilder = rsocketRequesterBuilder;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionRSocketMessageHandler functionRSocketMessageHandler(RSocketStrategies rSocketStrategies,
		ObjectProvider<RSocketMessageHandlerCustomizer> customizers) {

		FunctionRSocketMessageHandler rsocketMessageHandler = new FunctionRSocketMessageHandler();
		rsocketMessageHandler.setRSocketStrategies(rSocketStrategies);
		customizers.orderedStream().forEach((customizer) -> customizer.customize(rsocketMessageHandler));
		registerFunctionsWithRSocketHandler(rsocketMessageHandler);
		return rsocketMessageHandler;
	}

	private void registerFunctionsWithRSocketHandler(FunctionRSocketMessageHandler rsocketMessageHandler) {
		String definition = this.functionProperties.getDefinition();
		if (StringUtils.hasText(definition)) {
			String rootFunctionName = registerRSocketForwardingFunctionIfNecessary(definition);
			//TODO externalize content-type
			FunctionInvocationWrapper function = this.functionCatalog.lookup(definition, "application/json");
			rsocketMessageHandler.registerFunctionHandler(new RSocketListenerFunction(function), rootFunctionName);
		}
		else {
			this.functionCatalog.getNames(null)
				.forEach((name) -> {
					FunctionInvocationWrapper function = this.functionCatalog.lookup(name, "application/json");
					rsocketMessageHandler.registerFunctionHandler(new RSocketListenerFunction(function), name);
				});
		}
	}

	private String registerRSocketForwardingFunctionIfNecessary(String definition) {
		String[] names = StringUtils.delimitedListToStringArray(definition.replaceAll(",", "|").trim(), "|");
		String rootFunctionName = names[0];
		for (String name : names) {
			if (!this.applicationContext.containsBean(name)) { // this means RSocket
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Registering RSocket forwarder for '" + name + "' function.");
				}
				String[] functionToRSocketDefinition = StringUtils.delimitedListToStringArray(name, ">");
				Assert.isTrue(functionToRSocketDefinition.length == 2, "Must only contain one output redirect");
				FunctionInvocationWrapper function =
					this.functionCatalog.lookup(functionToRSocketDefinition[0], "application/json");
				rootFunctionName = function.getFunctionDefinition();
				String forwardingUrl = functionToRSocketDefinition[1];
				String remoteFunctionRoute = forwardingUrl.substring(forwardingUrl.lastIndexOf('/') + 1);
				forwardingUrl = forwardingUrl.substring(0, forwardingUrl.lastIndexOf('/'));
				RSocketRequester rsocketRequester;

				if (WS_URI_PATTERN.matcher(forwardingUrl).matches()) {
					rsocketRequester = this.rsocketRequesterBuilder.websocket(URI.create(forwardingUrl));
				}
				else {
					String[] hostPort = StringUtils.delimitedListToStringArray(forwardingUrl, ":");
					rsocketRequester = this.rsocketRequesterBuilder.tcp(hostPort[0], Integer.parseInt(hostPort[1]));
				}

				RSocketForwardingFunction rsocketFunction =
					new RSocketForwardingFunction(function, rsocketRequester, remoteFunctionRoute);
				FunctionRegistration<RSocketForwardingFunction> functionRegistration =
					new FunctionRegistration<>(rsocketFunction, name);
				functionRegistration.type(
					FunctionTypeUtils.discoverFunctionTypeFromClass(RSocketForwardingFunction.class));
				((FunctionRegistry) this.functionCatalog).register(functionRegistration);
			}
		}

		return rootFunctionName;
	}

}
