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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Main configuration class for components required to support RSocket integration with spring-cloud-function.
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({FunctionProperties.class, RSocketFunctionProperties.class})
public class RSocketAutoConfiguration {

	private static Log logger = LogFactory.getLog(RSocketAutoConfiguration.class);


	@Bean
	public FunctionToDestinationBinder functionToDestinationBinder(FunctionCatalog functionCatalog,
			FunctionProperties functionProperties, RSocketFunctionProperties rSocketFunctionProperties) {
		return new FunctionToDestinationBinder(functionCatalog, functionProperties, rSocketFunctionProperties);
	}

	/**
	 *
	 */
	private static class FunctionToDestinationBinder implements InitializingBean, DisposableBean, ApplicationContextAware {

		private final FunctionCatalog functionCatalog;

		private final FunctionProperties functionProperties;

		private final RSocketFunctionProperties rSocketFunctionProperties;

		private RSocketListenerFunction invocableFunction;

		private GenericApplicationContext context;

		FunctionToDestinationBinder(FunctionCatalog functionCatalog, FunctionProperties functionProperties,
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
			//TODO externalize content-type
			FunctionInvocationWrapper function = functionCatalog.lookup(definition, "application/json");
			if (function.isSupplier()) {
				throw new UnsupportedOperationException("Supplier is not currently supported for RSocket interaction");
			}

			InetSocketAddress bindAddress = InetSocketAddress
					.createUnresolved(this.rSocketFunctionProperties.getBindAddress(), this.rSocketFunctionProperties.getBindPort());

			if (this.invocableFunction == null) {
				this.invocableFunction = new RSocketListenerFunction(function, bindAddress);
				this.invocableFunction.start();
			}
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
					FunctionInvocationWrapper function = functionCatalog.lookup(functionToRSocketDefinition[0], "application/json");

					String[] hostPort = StringUtils.delimitedListToStringArray(functionToRSocketDefinition[1], ":");
					InetSocketAddress outputAddress = InetSocketAddress
							.createUnresolved(hostPort[0], Integer.valueOf(hostPort[1]));

					RSocketForwardingFunction rsocketFunction = new RSocketForwardingFunction(function, outputAddress);
					FunctionRegistration functionRegistration = new FunctionRegistration(rsocketFunction, name);

					functionRegistration.type(FunctionTypeUtils.discoverFunctionTypeFromClass(RSocketListenerFunction.class));
					((FunctionRegistry) this.functionCatalog).register(functionRegistration);
				}
			}
		}

		@Override
		public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
			this.context = (GenericApplicationContext) applicationContext;
		}

		@Override
		public void destroy() throws Exception {
			if (this.invocableFunction != null) {
				this.invocableFunction.stop();
			}
		}
	}
}
