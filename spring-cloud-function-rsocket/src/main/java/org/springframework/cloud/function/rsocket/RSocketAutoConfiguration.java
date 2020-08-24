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

import java.net.InetSocketAddress;

import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.messaging.rsocket.RSocketConnectorConfigurer;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Main configuration class for components required to support RSocket integration with
 * spring-cloud-function.
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ FunctionProperties.class, RSocketFunctionProperties.class })
@ConditionalOnProperty(name = FunctionProperties.PREFIX + ".rsocket.enabled", matchIfMissing = true)
@AutoConfigureBefore(name = "io.rsocket.routing.client.spring.RoutingClientAutoConfiguration")
public class RSocketAutoConfiguration {

	private static Log logger = LogFactory.getLog(RSocketAutoConfiguration.class);

	@Bean
	public FunctionToRSocketBinder functionToDestinationBinder(FunctionCatalog functionCatalog,
			FunctionProperties functionProperties, RSocketFunctionProperties rSocketFunctionProperties) {
		return new FunctionToRSocketBinder(functionCatalog, functionProperties, rSocketFunctionProperties);
	}

	@Bean
	@ConditionalOnClass(name = "io.rsocket.routing.client.spring.RoutingClientAutoConfiguration")
	//TODO: move to broker specific auto config
	public RSocketConnectorConfigurer functionRSocketConnectorConfigurer(
			FunctionToRSocketBinder binder) {
		return connector -> connector.acceptor(SocketAcceptor.with(binder.getRSocket()));
	}

	/**
	 *
	 */
	private static class FunctionToRSocketBinder implements InitializingBean, ApplicationContextAware, SmartLifecycle {

		private final FunctionCatalog functionCatalog;

		private final FunctionProperties functionProperties;

		private final RSocketFunctionProperties rSocketFunctionProperties;

		private RSocketListenerFunction invocableFunction;

		private GenericApplicationContext context;

		private boolean started;

		FunctionToRSocketBinder(FunctionCatalog functionCatalog, FunctionProperties functionProperties,
				RSocketFunctionProperties rSocketFunctionProperties) {
			this.functionCatalog = functionCatalog;
			this.functionProperties = functionProperties;
			this.rSocketFunctionProperties = rSocketFunctionProperties;
		}

		@Override
		public void afterPropertiesSet() throws Exception {
			String definition = this.functionProperties.getDefinition();
			if (!StringUtils.hasText(definition)) {
				FunctionInvocationWrapper f = this.functionCatalog.lookup("");
				if (f != null) {
					definition = f.getFunctionDefinition();
				}
			}
			Assert.isTrue(StringUtils.hasText(definition), "Failed to determine target function for RSocket.");
			this.registerRsocketForwardingFunctionIfNecessary(definition);
			// TODO externalize content-type
			FunctionInvocationWrapper function = functionCatalog.lookup(definition, "application/json");
			if (function.isSupplier()) {
				throw new UnsupportedOperationException("Supplier is not currently supported for RSocket interaction");
			}

			if (StringUtils.hasText(rSocketFunctionProperties.getBindAddress())
					&& rSocketFunctionProperties.getBindPort() != null) {
				InetSocketAddress bindAddress = InetSocketAddress.createUnresolved(
						this.rSocketFunctionProperties.getBindAddress(), this.rSocketFunctionProperties.getBindPort());
				this.invocableFunction = new RSocketListenerFunction(function, bindAddress);
			}
			else {
				this.invocableFunction = new RSocketListenerFunction(function, null);
			}
		}

		RSocket getRSocket() {
			if (this.invocableFunction == null) {
				return null;
			}
			return this.invocableFunction.getRsocket();
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		private void registerRsocketForwardingFunctionIfNecessary(String definition) {
			String[] names = StringUtils.delimitedListToStringArray(definition.replaceAll(",", "|").trim(), "|");

			for (String name : names) {
				if (!this.context.containsBean(name)) { // this means RSocket
					if (logger.isDebugEnabled()) {
						logger.debug("Registering rsocket forwarder for '" + name + "' function.");
					}
					String[] functionToRSocketDefinition = StringUtils.delimitedListToStringArray(name, ">");
					Assert.isTrue(functionToRSocketDefinition.length == 2, "Must only contain one output redirect");
					FunctionInvocationWrapper function = functionCatalog.lookup(functionToRSocketDefinition[0],
							"application/json");

					String[] hostPort = StringUtils.delimitedListToStringArray(functionToRSocketDefinition[1], ":");
					InetSocketAddress outputAddress = InetSocketAddress.createUnresolved(hostPort[0],
							Integer.valueOf(hostPort[1]));

					RSocketForwardingFunction rsocketFunction = new RSocketForwardingFunction(function, outputAddress);
					FunctionRegistration functionRegistration = new FunctionRegistration(rsocketFunction, name);

					functionRegistration
							.type(FunctionTypeUtils.discoverFunctionTypeFromClass(RSocketListenerFunction.class));
					((FunctionRegistry) this.functionCatalog).register(functionRegistration);
				}
			}
		}

		@Override
		public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
			this.context = (GenericApplicationContext) applicationContext;
		}

		@Override
		public void start() {
			if (!this.isRunning() && this.invocableFunction != null) {
				this.invocableFunction.start();
			}
		}

		@Override
		public void stop() {
			if (this.isRunning() && this.invocableFunction != null) {
				this.invocableFunction.stop();
			}
		}

		@Override
		public boolean isRunning() {
			return this.started;
		}

	}

}
