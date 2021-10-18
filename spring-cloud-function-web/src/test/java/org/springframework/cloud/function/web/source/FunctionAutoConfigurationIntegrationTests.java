/*
 * Copyright 2018-2019 the original author or authors.
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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

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
import org.springframework.cloud.function.web.source.FunctionAutoConfigurationIntegrationTests.ApplicationConfiguration;
import org.springframework.cloud.function.web.source.FunctionAutoConfigurationIntegrationTests.RestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.util.SocketUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
@SpringBootTest(classes = { RestConfiguration.class,
		ApplicationConfiguration.class }, webEnvironment = WebEnvironment.DEFINED_PORT, properties = {
				"spring.cloud.function.web.export.sink.url=http://localhost:${server.port}",
				"spring.cloud.function.web.export.source.url=http://localhost:${server.port}",
				"spring.cloud.function.web.export.sink.name=origin|uppercase"
				// "spring.cloud.function.web.export.debug=true"
				})
@Disabled
public class FunctionAutoConfigurationIntegrationTests {

	@Autowired
	private SupplierExporter forwarder;

	@Autowired
	private RestConfiguration app;

	@BeforeAll
	public static void init() {
		System.setProperty("server.port", "" + SocketUtils.findAvailableTcpPort());
	}

	@AfterAll
	public static void close() {
		System.clearProperty("server.port");
	}

	@Test
	public void copiesMessages() throws Exception {
		int count = 0;
		while (this.forwarder.isRunning() && count++ < 10) {
			Thread.sleep(20);
		}
		// It completed
		assertThat(this.forwarder.isOk()).isTrue();
		assertThat(this.app.inputs).contains("\"HELLO\"");
		assertThat(this.app.inputs).contains("\"WORLD\"");
	}

	@EnableAutoConfiguration
	@TestConfiguration
	public static class ApplicationConfiguration {

		@Bean
		public Function<String, String> uppercase() {
			return value -> value.toUpperCase();
		}

	}

	@TestConfiguration
	@RestController
	public static class RestConfiguration {

		private static Log logger = LogFactory.getLog(RestConfiguration.class);

		private List<String> inputs = new ArrayList<>();

		private Iterator<String> outputs = Arrays.asList("hello", "world").iterator();

		@GetMapping("/")
		ResponseEntity<String> home() {
			logger.info("HOME");
			if (this.outputs.hasNext()) {
				return ResponseEntity.ok(this.outputs.next());
			}
			return ResponseEntity.notFound().build();
		}

		@PostMapping("/")
		void accept(@RequestBody String body) {
			logger.info("ACCEPT");
			this.inputs.add(body);
		}

	}

}
