/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.cloud.function.serverless.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.context.ConfigurableWebServerApplicationContext;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.WebServerException;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.cloud.function.serverless.web.ServerlessMVC.ProxyServletConfig;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * @author Oleg Zhurakousky
 * @since 4.x
 */
@Configuration(proxyBeanMethods = false)
public class ServerlessAutoConfiguration {
	private static Log logger = LogFactory.getLog(ServerlessAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public ServletWebServerFactory servletWebServerFactory() {
		return new ServerlessServletWebServerFactory();
	}

	public static class ServerlessServletWebServerFactory
			implements ServletWebServerFactory, ApplicationContextAware, InitializingBean {

		private ConfigurableWebServerApplicationContext applicationContext;

		@Override
		public WebServer getWebServer(ServletContextInitializer... initializers) {
			return new WebServer() {

				@Override
				public void stop() throws WebServerException {
					// NOP
				}

				@Override
				public void start() throws WebServerException {
				}

				@Override
				public int getPort() {
					return 0;
				}
			};
		}

		@Override
		public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
			this.applicationContext = (ConfigurableWebServerApplicationContext) applicationContext;
		}

		@Override
		public void afterPropertiesSet() throws Exception {
			if (applicationContext instanceof ServletWebServerApplicationContext servletApplicationContet) {
				logger.info("Configuring Serverless Web Container");
				ServerlessServletContext servletContext = new ServerlessServletContext();
				servletApplicationContet.setServletContext(servletContext);
				DispatcherServlet dispatcher = applicationContext.getBean(DispatcherServlet.class);
				try {
					logger.info("Initializing DispatcherServlet");
					dispatcher.init(new ProxyServletConfig(servletApplicationContet.getServletContext()));
					logger.info("Initalized DispatcherServlet");
				}
				catch (Exception e) {
					throw new IllegalStateException("Faild to create Spring MVC DispatcherServlet proxy", e);
				}
				for (ServletContextInitializer initializer : new ServletContextInitializerBeans(this.applicationContext)) {
					System.out.println("==> INITIALIZING  " + initializer);
					initializer.onStartup(servletContext);
				}
			}
		}
	}
}
