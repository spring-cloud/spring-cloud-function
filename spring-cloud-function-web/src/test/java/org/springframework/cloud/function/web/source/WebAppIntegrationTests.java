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

package org.springframework.cloud.function.web.source;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.function.web.RestApplication;
import org.springframework.cloud.function.web.source.WebAppIntegrationTests.ApplicationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.SocketUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
@SpringBootTest(classes = { RestApplication.class, ApplicationConfiguration.class },
	webEnvironment = WebEnvironment.DEFINED_PORT, properties = {
		"spring.main.web-application-type=reactive",
		"spring.cloud.function.web.export.sink.url=http://localhost:${server.port}/values",
		// manually so we know the webapp is listening when we start
		"spring.cloud.function.web.export.autoStartup=false" })
public class WebAppIntegrationTests {

	private static Log logger = LogFactory.getLog(WebAppIntegrationTests.class);

	@Autowired
	private SupplierExporter forwarder;

	@Autowired
	private ApplicationConfiguration app;

	@BeforeAll
	public static void init() {
		System.setProperty("server.port", "" + SocketUtils.findAvailableTcpPort());
	}

	@AfterAll
	public static void close() {
		System.clearProperty("server.port");
	}

	@Test
	@Disabled
	public void posts() throws Exception {
		this.forwarder.start();
		this.app.latch.await(10, TimeUnit.SECONDS);
		assertThat(this.app.values).hasSize(1);
	}

	@EnableAutoConfiguration
	@TestConfiguration
	@RestController
	public static class ApplicationConfiguration {

		private List<String> values = new ArrayList<>();

		private CountDownLatch latch = new CountDownLatch(1);

		@Bean
		public Supplier<String> word() {
			return () -> "foo";
		}

		// An endpoint to catch the values being exported
		@PostMapping("/values")
		public String value(@RequestBody String body) {
			logger.info("Body: " + body);
			this.values.add(body);
			this.latch.countDown();
			return "ok";
		}

	}

}
