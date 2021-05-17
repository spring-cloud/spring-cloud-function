/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.function.adapter.aws;


import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
/**
 *
 * @author Oleg Zhurakousky
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "spring.main.web-application-type=servlet")
@ContextConfiguration(classes = {
		CustomRuntimeEventLoopTest.CustomRuntimeEmulatorConfiguration.class })
public class CustomRuntimeEventLoopTest {

	@LocalServerPort
	private int port;

	@Autowired
	private CustomRuntimeEmulatorConfiguration configuration;

	@SuppressWarnings("unchecked")
	private Map<String, String> getEnvironment() throws Exception {
		Map<String, String> env = System.getenv();
		Field field = env.getClass().getDeclaredField("m");
		field.setAccessible(true);
		return (Map<String, String>) field.get(env);
	}

	@BeforeEach
	public void before() {
		System.setProperty("CustomRuntimeEventLoop.continue", "true");
	}

	@Test
	@DirtiesContext
	public void testDefaultFunctionLookup() throws Exception {
		this.getEnvironment().put("AWS_LAMBDA_RUNTIME_API", "localhost:" + port);

		configuration.inputQueue.clear();
		configuration.inputQueue.addAll(Arrays.asList("\"ricky\"", "\"julien\"", "\"bubbles\""));

		try (ConfigurableApplicationContext userContext = new SpringApplicationBuilder(SingleFunctionConfiguration.class)
					.web(WebApplicationType.NONE).run(
						"--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true")) {
			ExecutorService executor = Executors.newSingleThreadExecutor();
			executor.execute(() -> {
				CustomRuntimeEventLoop.eventLoop(userContext);
			});

			executor.shutdown();
			assertThat(executor.awaitTermination(2000, TimeUnit.MILLISECONDS)).isTrue();

			assertThat(configuration.output).size().isEqualTo(3);
			assertThat(configuration.output.get(0)).isEqualTo("\"RICKY\"");
			assertThat(configuration.output.get(1)).isEqualTo("\"JULIEN\"");
			assertThat(configuration.output.get(2)).isEqualTo("\"BUBBLES\"");
		}
	}

	@Test
	@DirtiesContext
	public void testDefaultFunctionAsComponentLookup() throws Exception {
		this.getEnvironment().put("AWS_LAMBDA_RUNTIME_API", "localhost:" + port);

		configuration.inputQueue.clear();
		configuration.inputQueue.addAll(Arrays.asList("\"ricky\"", "\"julien\"", "\"bubbles\""));

		try (ConfigurableApplicationContext userContext = new SpringApplicationBuilder(PersonFunction.class)
					.web(WebApplicationType.NONE).run(
						"--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true")) {
			ExecutorService executor = Executors.newSingleThreadExecutor();
			executor.execute(() -> {
				CustomRuntimeEventLoop.eventLoop(userContext);
			});

			executor.shutdown();
			assertThat(executor.awaitTermination(2000, TimeUnit.MILLISECONDS)).isTrue();

			assertThat(configuration.output).size().isEqualTo(3);
			assertThat(configuration.output.get(0)).isEqualTo("{\"name\":\"RICKY\"}");
			assertThat(configuration.output.get(1)).isEqualTo("{\"name\":\"JULIEN\"}");
			assertThat(configuration.output.get(2)).isEqualTo("{\"name\":\"BUBBLES\"}");
		}
	}

	@Test
	@DirtiesContext
	public void test_HANDLERlookupAndPojoFunction() throws Exception {
		this.getEnvironment().put("AWS_LAMBDA_RUNTIME_API", "localhost:" + port);
		this.getEnvironment().put("_HANDLER", "uppercasePerson");

		configuration.inputQueue.clear();
		configuration.inputQueue.addAll(Arrays.asList("{\"name\":\"ricky\"}",
				"{\"name\":\"julien\"}", "{\"name\":\"bubbles\"}"));
		try (ConfigurableApplicationContext userContext = new SpringApplicationBuilder(MultipleFunctionConfiguration.class)
					.web(WebApplicationType.NONE).run(
						"--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true")) {
			ExecutorService executor = Executors.newSingleThreadExecutor();
			executor.execute(() -> {
				CustomRuntimeEventLoop.eventLoop(userContext);
			});

			executor.shutdown();
			assertThat(executor.awaitTermination(2000, TimeUnit.MILLISECONDS)).isTrue();

			assertThat(configuration.output).size().isEqualTo(3);
			assertThat(configuration.output.get(0)).isEqualTo("{\"name\":\"RICKY\"}");
			assertThat(configuration.output.get(1)).isEqualTo("{\"name\":\"JULIEN\"}");
			assertThat(configuration.output.get(2)).isEqualTo("{\"name\":\"BUBBLES\"}");
		}
	}

	@Test
	@DirtiesContext
	public void test_definitionLookupAndComposition() throws Exception {
		this.getEnvironment().put("AWS_LAMBDA_RUNTIME_API", "localhost:" + port);
		System.setProperty("spring.cloud.function.definition", "toPersonJson|uppercasePerson");

		configuration.inputQueue.clear();
		configuration.inputQueue.addAll(Arrays.asList("\"ricky\"", "\"julien\"", "\"bubbles\""));

		try (ConfigurableApplicationContext userContext = new SpringApplicationBuilder(MultipleFunctionConfiguration.class)
					.web(WebApplicationType.NONE).run(
						"--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true")) {
			ExecutorService executor = Executors.newSingleThreadExecutor();
			executor.execute(() -> {
				CustomRuntimeEventLoop.eventLoop(userContext);
			});

			executor.shutdown();
			assertThat(executor.awaitTermination(2000, TimeUnit.MILLISECONDS)).isTrue();

			assertThat(configuration.output).size().isEqualTo(3);
			assertThat(configuration.output.get(0)).isEqualTo("{\"name\":\"RICKY\"}");
			assertThat(configuration.output.get(1)).isEqualTo("{\"name\":\"JULIEN\"}");
			assertThat(configuration.output.get(2)).isEqualTo("{\"name\":\"BUBBLES\"}");
		}
	}

	@SpringBootConfiguration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	protected static class CustomRuntimeEmulatorConfiguration {

		BlockingQueue<String> inputQueue = new ArrayBlockingQueue<>(3);

		List<String> output = new ArrayList<>();

		@Bean("2018-06-01/runtime/invocation/consume/response")
		public Consumer<Message<String>> consume() {
			return v -> output.add(v.getPayload());
		}

		@Bean("2018-06-01/runtime/invocation/next")
		public Supplier<Message<String>> supply() {

			return () -> {
				try {
					String value = inputQueue.poll(Long.MAX_VALUE, TimeUnit.SECONDS);
					if (inputQueue.peek() == null) {
						System.setProperty("CustomRuntimeEventLoop.continue", "false");
					}
					return MessageBuilder.withPayload(value)
							.setHeader("Lambda-Runtime-Aws-Request-Id", "consume")
							.setHeader("Content-Type",
									MimeTypeUtils.APPLICATION_JSON)
							.build();
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(e);
				}
			};
		}
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class SingleFunctionConfiguration {
		@Bean
		public Function<String, String> uppercase() {
			return v -> v.toUpperCase();
		}
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class MultipleFunctionConfiguration {
		@Bean
		public Function<String, String> uppercase() {
			return v -> v.toUpperCase();
		}

		@Bean
		public Function<String, String> toPersonJson() {
			return v -> "{\"name\":\"" + v + "\"}";
		}

		@Bean
		public Function<Person, Person> uppercasePerson() {
			return p -> new Person(p.getName().toUpperCase());
		}
	}

	@EnableAutoConfiguration
	@Component
	public static class PersonFunction implements Function<Person, Person> {

		@Override
		public Person apply(Person input) {
			return new Person(input.getName().toUpperCase());
		}
	}

	public static class Person {
		private String name;

		public Person() {

		}

		public Person(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

}
