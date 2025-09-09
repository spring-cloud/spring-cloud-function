/*
 * Copyright 2019-present the original author or authors.
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
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.test.client.TestRestTemplate;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionalSpringApplication;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
*
* @author Oleg Zhurakousky
* @author Chris Bono
* @since 2.1
*/
public class FunctionEndpointInitializerTests {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testEmptyBodyRequestParameters() throws Exception {
		int port = startServerAndWaitForPort(BeansConfiguration.class, false);
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		Map<String, String> params = new HashMap<>();
		params.put("fname", "Jim");
		params.put("lname", "Lahey");

		HttpHeaders headers = new HttpHeaders();
		headers.set("Accept", "application/json");
		HttpEntity entity = new HttpEntity(headers);

		String urlTemplate = UriComponentsBuilder.fromUriString("http://localhost:" + port + "/nullPayload")
				.queryParam("fname", "Jim").queryParam("lname", "Lahey").encode().toUriString();

		ResponseEntity<String> response = testRestTemplate.exchange(urlTemplate, HttpMethod.GET, entity, String.class);
		String res = response.getBody();
		assertThat(res).contains("Jim");
		assertThat(res).contains("Lahey");
	}

	@Test
	public void testNonExistingFunction() throws Exception {
		int port = startServerAndWaitForPort(ApplicationConfiguration.class);
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		ResponseEntity<String> response = testRestTemplate
				.postForEntity(new URI("http://localhost:" + port + "/foo"), "stressed", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	public void testConsumerMapping() throws Exception {
		int port = startServerAndWaitForPort(ConsumerConfiguration.class);
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		ResponseEntity<String> response = testRestTemplate
				.postForEntity(new URI("http://localhost:" + port + "/uppercase"), "stressed", String.class);
		assertThat(response.getBody()).isNull();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
	}

	@Test
	public void testSingleFunctionMapping() throws Exception {
		int port = startServerAndWaitForPort(ApplicationConfiguration.class);
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		ResponseEntity<String> response = testRestTemplate
				.postForEntity(new URI("http://localhost:" + port + "/uppercase"), "stressed", String.class);
		assertThat(response.getBody()).isEqualTo("STRESSED");
		response = testRestTemplate.postForEntity(new URI("http://localhost:" + port + "/reverse"), "stressed", String.class);
		assertThat(response.getBody()).isEqualTo("desserts");
	}

	@Test
	public void testCompositionFunctionMapping() throws Exception {
		int port = startServerAndWaitForPort(ApplicationConfiguration.class);
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		ResponseEntity<String> response = testRestTemplate
				.postForEntity(new URI("http://localhost:" + port + "/uppercase,lowercase,reverse"), "stressed", String.class);
		assertThat(response.getBody()).isEqualTo("desserts");
	}

	@Test
	public void testGetWithtFunction() throws Exception {
		int port = startServerAndWaitForPort(ApplicationConfiguration.class);
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		ResponseEntity<String> response = testRestTemplate
				.getForEntity(new URI("http://localhost:" + port + "/reverse/stressed"), String.class);
		assertThat(response.getBody()).isEqualTo("desserts");
	}

	@Test
	public void testGetWithtSupplier() throws Exception {
		int port = startServerAndWaitForPort(ApplicationConfiguration.class);
		TestRestTemplate testRestTemplate = new TestRestTemplate();
		ResponseEntity<String> response = testRestTemplate
				.getForEntity(new URI("http://localhost:" + port + "/supplier"), String.class);
		assertThat(response.getBody()).isEqualTo("Jim Lahey");
	}

	private int startServerAndWaitForPort(Class<?> primaryAppConfig, boolean functional) throws InterruptedException {
		ConfigurableApplicationContext context = functional
				? FunctionalSpringApplication.run(primaryAppConfig, "--server.port=0")
						: SpringApplication.run(primaryAppConfig, "--server.port=0");
		await()
			.pollDelay(Duration.ofMillis(500))
			.pollInterval(Duration.ofMillis(500))
			.atMost(Duration.ofSeconds(3))
			.untilAsserted(() -> {
				String port = context.getEnvironment().getProperty("local.server.port");
				assertThat(port).as("Unable to get 'local.server.port' - server may not have started up").isNotEmpty();
			});
		return Integer.valueOf(context.getEnvironment().getProperty("local.server.port"));
	}

	private int startServerAndWaitForPort(Class<?> primaryAppConfig) throws InterruptedException {
		return this.startServerAndWaitForPort(primaryAppConfig, true);
	}

	@SpringBootConfiguration
	protected static class ConsumerConfiguration
			implements ApplicationContextInitializer<GenericApplicationContext> {

		public Consumer<String> consume() {
			return v -> System.out.println(v);
		}

		@Override
		public void initialize(GenericApplicationContext applicationContext) {

			applicationContext.registerBean("consume", FunctionRegistration.class,
					() -> new FunctionRegistration<>(consume())
						.type(ResolvableType.forClassWithGenerics(Consumer.class, String.class).getType()));
		}

	}

	@EnableAutoConfiguration
	@Configuration
	protected static class BeansConfiguration {
		@Bean
		public BiFunction<String, Map<String, Object>, Map<String, Object>> nullPayload() {
			return (p, h) -> {
				return h;
			};
		}
	}


	@SpringBootConfiguration
	protected static class ApplicationConfiguration
			implements ApplicationContextInitializer<GenericApplicationContext> {

		public Supplier<String> supplier() {
			return () -> "Jim Lahey";
		}

		public Function<String, String> uppercase() {
			return s -> s.toUpperCase(Locale.ROOT);
		}

		public Function<String, String> lowercase() {
			return s -> s.toLowerCase(Locale.ROOT);
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
						.type(FunctionTypeUtils.functionType(String.class, String.class)));
			applicationContext.registerBean("reverse", FunctionRegistration.class,
					() -> new FunctionRegistration<>(reverse())
						.type(FunctionTypeUtils.functionType(String.class, String.class)));
			applicationContext.registerBean("lowercase", FunctionRegistration.class,
					() -> new FunctionRegistration<>(lowercase())
						.type(FunctionTypeUtils.functionType(String.class, String.class)));
			applicationContext.registerBean("supplier", FunctionRegistration.class,
					() -> new FunctionRegistration<>(supplier())
						.type(FunctionTypeUtils.supplierType(String.class)));
		}

	}

}
