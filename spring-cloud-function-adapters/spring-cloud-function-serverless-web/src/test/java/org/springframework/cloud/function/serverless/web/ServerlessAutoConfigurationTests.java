/*
 * Copyright 2024-present the original author or authors.
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

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.WebServerException;
import org.springframework.boot.web.server.servlet.ServletWebServerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerlessAutoConfigurationTests {

	@Test
	void autoConfigurationOrderingCoversSupportedServletContainers() {
		AutoConfiguration autoConfiguration = ServerlessAutoConfiguration.class.getAnnotation(AutoConfiguration.class);
		assertThat(autoConfiguration).isNotNull();

		assertThat(autoConfiguration.beforeName()).contains(
				"org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration",
				"org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration",
				"org.springframework.boot.jetty.autoconfigure.servlet.JettyServletWebServerAutoConfiguration",
				"org.springframework.boot.undertow.autoconfigure.servlet.UndertowServletWebServerAutoConfiguration");
	}

	@Test
	void missingServerlessAutoConfigurationFailsWithUsefulError() {
		System.setProperty(ServerlessMVC.INIT_TIMEOUT, "5000");
		ServerlessMVC mvc = ServerlessMVC.INSTANCE(ApplicationWithoutServerlessAutoConfiguration.class);
		HttpServletRequest request = new ServerlessHttpServletRequest(null, "GET", "/hello");
		ServerlessHttpServletResponse response = new ServerlessHttpServletResponse();

		assertThatThrownBy(() -> mvc.service(request, response))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Application context failed to initialize")
				.hasMessageContaining("Ensure ServerlessAutoConfiguration is active and selected as the ServletWebServerFactory.");
	}

	@Test
	void customServletWebServerFactoryFailsWithUsefulErrorInsteadOfNpe() {
		System.setProperty(ServerlessMVC.INIT_TIMEOUT, "5000");
		ServerlessMVC mvc = ServerlessMVC.INSTANCE(ApplicationWithCustomServletWebServerFactory.class);
		HttpServletRequest request = new ServerlessHttpServletRequest(null, "GET", "/hello");
		ServerlessHttpServletResponse response = new ServerlessHttpServletResponse();

		assertThatThrownBy(() -> mvc.service(request, response))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Application context failed to initialize")
				.hasMessageContaining("Ensure ServerlessAutoConfiguration is active and selected as the ServletWebServerFactory.");
	}

	@Test
	void failedStartupGetServletContextThrowsUsefulErrorInsteadOfNpe() {
		System.setProperty(ServerlessMVC.INIT_TIMEOUT, "5000");
		ServerlessMVC mvc = ServerlessMVC.INSTANCE(ApplicationWithCustomServletWebServerFactory.class);

		assertThatThrownBy(mvc::getServletContext)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Application context failed to initialize")
				.hasMessageContaining("Ensure ServerlessAutoConfiguration is active and selected as the ServletWebServerFactory.");
	}

	@AfterEach
	void clearInitTimeoutOverride() {
		System.clearProperty(ServerlessMVC.INIT_TIMEOUT);
	}

	@RestController
	@SpringBootApplication(excludeName = "org.springframework.cloud.function.serverless.web.ServerlessAutoConfiguration")
	static class ApplicationWithoutServerlessAutoConfiguration {

		@GetMapping("/hello")
		String hello() {
			return "hello";
		}
	}

	@RestController
	@SpringBootApplication
	static class ApplicationWithCustomServletWebServerFactory {

		@GetMapping("/hello")
		String hello() {
			return "hello";
		}

		@org.springframework.context.annotation.Bean
		ServletWebServerFactory customServletWebServerFactory() {
			return (initializers) -> new WebServer() {
				@Override
				public void start() throws WebServerException {
				}

				@Override
				public void stop() throws WebServerException {
				}

				@Override
				public int getPort() {
					return 0;
				}
			};
		}
	}

}
