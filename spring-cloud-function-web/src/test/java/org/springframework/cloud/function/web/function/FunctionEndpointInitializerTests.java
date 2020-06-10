/*
 * Copyright 2019-2019 the original author or authors.
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

package org.springframework.cloud.function.web.function;

import java.net.URI;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.FunctionalSpringApplication;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
*
* @author Oleg Zhurakousky
* @since 2.1
*
*/
public class FunctionEndpointInitializerTests {

	@BeforeEach
	public void init() throws Exception {
		String port = "" + SocketUtils.findAvailableTcpPort();
		System.setProperty("server.port", port);
	}

	@AfterEach
	public void close() throws Exception {
		System.clearProperty("server.port");
	}

	@Test
	public void testNonExistingFunction() throws Exception {
		FunctionalSpringApplication.run(ApplicationConfiguration.class);
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		String port = System.getProperty("server.port");
		Thread.sleep(200);
		ResponseEntity<String> response = testRestTemplate
				.postForEntity(new URI("http://localhost:" + port + "/foo"), "stressed", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	public void testSingleFunctionMapping() throws Exception {
		FunctionalSpringApplication.run(ApplicationConfiguration.class);
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		String port = System.getProperty("server.port");
		Thread.sleep(200);
		ResponseEntity<String> response = testRestTemplate
				.postForEntity(new URI("http://localhost:" + port + "/uppercase"), "stressed", String.class);
		assertThat(response.getBody()).isEqualTo("STRESSED");
		response = testRestTemplate.postForEntity(new URI("http://localhost:" + port + "/reverse"), "stressed", String.class);
		assertThat(response.getBody()).isEqualTo("desserts");
	}

	@Test
	public void testCompositionFunctionMapping() throws Exception {
		FunctionalSpringApplication.run(ApplicationConfiguration.class);
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		String port = System.getProperty("server.port");
		Thread.sleep(200);
		ResponseEntity<String> response = testRestTemplate
				.postForEntity(new URI("http://localhost:" + port + "/uppercase,lowercase,reverse"), "stressed", String.class);
		assertThat(response.getBody()).isEqualTo("desserts");
	}

	@Test
	public void testGetWithtFunction() throws Exception {
		FunctionalSpringApplication.run(ApplicationConfiguration.class);
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		String port = System.getProperty("server.port");
		Thread.sleep(2000);
		ResponseEntity<String> response = testRestTemplate
				.getForEntity(new URI("http://localhost:" + port + "/reverse/stressed"), String.class);
		System.out.println();
		assertThat(response.getBody()).isEqualTo("desserts");
	}

	@Test
	public void testGetWithtSupplier() throws Exception {
		FunctionalSpringApplication.run(ApplicationConfiguration.class);
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		String port = System.getProperty("server.port");
		Thread.sleep(200);
		ResponseEntity<String> response = testRestTemplate
				.getForEntity(new URI("http://localhost:" + port + "/supplier"), String.class);
		assertThat(response.getBody()).isEqualTo("Jim Lahey");
	}


	@SpringBootConfiguration
	protected static class ApplicationConfiguration
			implements ApplicationContextInitializer<GenericApplicationContext> {

		public Supplier<String> supplier() {
			return () -> "Jim Lahey";
		}

		public Function<String, String> uppercase() {
			return s -> s.toUpperCase();
		}

		public Function<String, String> lowercase() {
			return s -> s.toLowerCase();
		}

		public Function<String, String> reverse() {
			return s -> {
				return new StringBuilder(s).reverse().toString();
			};
		}

		@Override
		public void initialize(GenericApplicationContext applicationContext) {
			applicationContext.registerBean("uppercase", FunctionRegistration.class,
					() -> new FunctionRegistration<>(uppercase())
						.type(FunctionType.from(String.class).to(String.class)));
			applicationContext.registerBean("reverse", FunctionRegistration.class,
					() -> new FunctionRegistration<>(reverse())
						.type(FunctionType.from(String.class).to(String.class)));
			applicationContext.registerBean("lowercase", FunctionRegistration.class,
					() -> new FunctionRegistration<>(lowercase())
						.type(FunctionType.from(String.class).to(String.class)));
			applicationContext.registerBean("supplier", FunctionRegistration.class,
					() -> new FunctionRegistration<>(supplier())
						.type(FunctionType.supplier(String.class)));
		}

	}

}
