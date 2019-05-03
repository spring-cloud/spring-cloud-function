/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.cloud.function.test;

import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.test.FunctionalSpringBootTest;
import org.springframework.cloud.function.test.FunctionalExporterTests.ApplicationConfiguration;
import org.springframework.cloud.function.web.source.SupplierExporter;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
@RunWith(SpringRunner.class)
@FunctionalSpringBootTest(classes = ApplicationConfiguration.class, webEnvironment = WebEnvironment.NONE, properties = {
		"spring.main.web-application-type=none",
		"spring.cloud.function.web.export.sink.url=http://localhost:${my.port}",
		"spring.cloud.function.web.export.source.url=http://localhost:${my.port}",
		"spring.cloud.function.web.export.sink.name=origin|uppercase",
		"spring.cloud.function.web.export.debug=true" })
public class FunctionalExporterTests {

	@Autowired
	private SupplierExporter forwarder;

	private static RestConfiguration app;

	private static ConfigurableApplicationContext context;

	@BeforeClass
	public static void init() throws Exception {
		String port = "" + SocketUtils.findAvailableTcpPort();
		System.setProperty("server.port", port);
		System.setProperty("my.port", port);
		context = SpringApplication.run(RestConfiguration.class,
				"--spring.main.web-application-type=reactive");
		app = context.getBean(RestConfiguration.class);
		// Sometimes the server doesn't start quick enough
		Thread.sleep(500L);
	}

	@AfterClass
	public static void close() {
		System.clearProperty("server.port");
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void words() throws Exception {
		int count = 0;
		while (this.forwarder.isRunning() && count++ < 10) {
			Thread.sleep(20);
		}
		// It completed
		assertThat(FunctionalExporterTests.app.inputs).contains("HELLO");
		assertThat(this.forwarder.isOk()).isTrue();
	}

	@SpringBootConfiguration
	protected static class ApplicationConfiguration
			implements ApplicationContextInitializer<GenericApplicationContext> {

		Function<Message<String>, Message<String>> uppercase() {
			return value -> MessageBuilder.withPayload(value.getPayload().toUpperCase())
					.copyHeaders(value.getHeaders()).build();
		}

		@Override
		public void initialize(GenericApplicationContext context) {
			context.registerBean("uppercase", FunctionRegistration.class,
					() -> new FunctionRegistration<>(uppercase()).type(
							FunctionType.from(String.class).to(String.class).message()));
		}

	}

}
