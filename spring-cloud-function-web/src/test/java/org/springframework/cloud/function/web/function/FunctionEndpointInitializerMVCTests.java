/*
 * Copyright 2019-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;


public class FunctionEndpointInitializerMVCTests {

	@Before
	public void init() throws Exception {
		String port = "" + SocketUtils.findAvailableTcpPort();
		System.setProperty("server.port", port);
	}

	@After
	public void close() throws Exception {
		System.clearProperty("server.port");
	}

	@Test
	public void testSingleFunctionMapping() throws Exception {
		SpringApplication.run(ApplicationConfiguration.class);
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		String port = System.getProperty("server.port");
		ResponseEntity<String> response = testRestTemplate.postForEntity(new URI("http://localhost:" + port + "/uppercase"), "stressed", String.class);
		assertThat(response.getBody()).isEqualTo("STRESSED");
		response = testRestTemplate.postForEntity(new URI("http://localhost:" + port + "/reverse"), "stressed", String.class);
		assertThat(response.getBody()).isEqualTo("desserts");
	}

	@Test
	public void testCompositionFunctionMapping() throws Exception {
		SpringApplication.run(ApplicationConfiguration.class);
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		String port = System.getProperty("server.port");
		ResponseEntity<String> response = testRestTemplate.postForEntity(new URI("http://localhost:" + port + "/uppercase,lowercase,reverse"), "stressed", String.class);
		assertThat(response.getBody()).isEqualTo("desserts");
	}


	@SpringBootApplication
	protected static class ApplicationConfiguration {

		@Bean
		public Function<String, String> uppercase() {
			return s -> s.toUpperCase();
		}

		@Bean
		public Function<String, String> lowercase() {
			return s -> s.toLowerCase();
		}

		@Bean
		public Function<String, String> reverse() {
			return s -> new StringBuilder(s).reverse().toString();
		}

//		@Override
//		public void initialize(GenericApplicationContext applicationContext) {
//			applicationContext.registerBean("uppercase", FunctionRegistration.class,
//					() -> new FunctionRegistration<>(uppercase())
//						.type(FunctionType.from(String.class).to(String.class)));
//			applicationContext.registerBean("reverse", FunctionRegistration.class,
//					() -> new FunctionRegistration<>(reverse())
//						.type(FunctionType.from(String.class).to(String.class)));
//			applicationContext.registerBean("lowercase", FunctionRegistration.class,
//					() -> new FunctionRegistration<>(lowercase())
//						.type(FunctionType.from(String.class).to(String.class)));
//		}

	}

}
