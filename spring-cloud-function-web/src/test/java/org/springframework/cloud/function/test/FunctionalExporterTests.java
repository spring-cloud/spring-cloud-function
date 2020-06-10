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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
@FunctionalSpringBootTest(classes = ApplicationConfiguration.class, webEnvironment = WebEnvironment.NONE, properties = {
		"spring.main.web-application-type=none",
		"spring.cloud.function.web.export.sink.url=http://localhost:${my.port}",
		"spring.cloud.function.web.export.source.url=http://localhost:${my.port}",
		"spring.cloud.function.web.export.sink.name=origin|uppercase",
		"spring.cloud.function.web.export.sink.contentType=text/plain",
		"spring.cloud.function.web.export.debug=true" })
public class FunctionalExporterTests {

	@Autowired
	private SupplierExporter forwarder;

	private static RestPojoConfiguration app;

	private static ConfigurableApplicationContext context;

	private static Map<String, Object> headers = new HashMap<>();

	@BeforeAll
	public static void init() throws Exception {
		headers.clear();
		String port = "" + SocketUtils.findAvailableTcpPort();
		System.setProperty("server.port", port);
		System.setProperty("my.port", port);
		context = SpringApplication.run(RestPojoConfiguration.class,
				"--spring.main.web-application-type=reactive");
		app = context.getBean(RestPojoConfiguration.class);
		// Sometimes the server doesn't start quick enough
		Thread.sleep(500L);
	}

	@AfterAll
	public static void close() {
		headers.clear();
		System.clearProperty("server.port");
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void words() throws Exception {
		int count = 0;
		while (this.forwarder.isRunning() && count++ < 10) {
			Thread.sleep(50);
		}
		// It completed
		assertThat(FunctionalExporterTests.app.inputs).contains("HELLO");
		assertThat(this.forwarder.isOk()).isTrue();
		assertThat(headers.containsKey("scf-sink-url"));
		assertThat(headers.containsKey("scf-func-name"));
	}

	@SpringBootConfiguration
	protected static class ApplicationConfiguration
			implements ApplicationContextInitializer<GenericApplicationContext> {

		Function<Message<Person>, Message<String>> uppercase() {
			return value -> {
				headers.putAll(value.getHeaders());
				return MessageBuilder.withPayload(value.getPayload().getName().toUpperCase())
					.copyHeaders(value.getHeaders()).build();
			};
		}

		@Override
		public void initialize(GenericApplicationContext context) {
			context.registerBean("uppercase", FunctionRegistration.class,
					() -> new FunctionRegistration<>(uppercase()).type(
							FunctionType.from(Person.class).to(String.class).message()));
		}
	}

}

class Person {
	private String name;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
