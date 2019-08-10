/*
 * Copyright 2017-2019 the original author or authors.
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

package org.springframework.cloud.function.deployer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

import org.junit.Test;
import reactor.core.publisher.Flux;

import org.springframework.boot.SpringApplication;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.0
 */
public class FunctionDeployerTests {

	/*
	 * Target function `class UpperCaseFunction implements Function<String, String>`
	 * Main/Start class present, no Spring configuration
	 */
	@Test
	public void testWithMainAndStartClassNoSpringConfiguration() throws Exception {
		String[] args = new String[] {
				"--spring.cloud.function.location=target/it/bootjar/target/bootjar-0.0.1.BUILD-SNAPSHOT-exec.jar",
				"--spring.cloud.function.function-class=function.example.UpperCaseFunction" };

		ApplicationContext context = SpringApplication.run(FunctionDeployerConfiguration.class, args);
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		Function<String, String> function = catalog.lookup("upperCaseFunction");

		assertThat(function.apply("bob")).isEqualTo("BOB");
		assertThat(function.apply("stacy")).isEqualTo("STACY");

		Function<Flux<String>, Flux<String>> functionAsFlux = catalog.lookup("upperCaseFunction");

		List<String> results = functionAsFlux.apply(Flux.just("bob", "stacy")).collectList().block();
		assertThat(results.get(0)).isEqualTo("BOB");
		assertThat(results.get(1)).isEqualTo("STACY");
	}

	/*
	 * Target function `class UpperCaseFunction implements Function<String, String>`
	 * No Main/Start class present, no Spring configuration
	 */
	@Test
	public void testNoMainAndNoStartClassAndNoSpringConfiguration() throws Exception {
		String[] args = new String[] {
				"--spring.cloud.function.location=target/it/bootjarnostart/target/bootjarnostart-0.0.1.BUILD-SNAPSHOT-exec.jar",
				"--spring.cloud.function.function-class=function.example.UpperCaseFunction" };

		ApplicationContext context = SpringApplication.run(FunctionDeployerConfiguration.class, args);
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		Function<String, String> function = catalog.lookup("upperCaseFunction");

		assertThat(function.apply("bob")).isEqualTo("BOB");
		assertThat(function.apply("stacy")).isEqualTo("STACY");

		Function<Flux<String>, Flux<String>> functionAsFlux = catalog.lookup("upperCaseFunction");

		List<String> results = functionAsFlux.apply(Flux.just("bob", "stacy")).collectList().block();
		assertThat(results.get(0)).isEqualTo("BOB");
		assertThat(results.get(1)).isEqualTo("STACY");
	}

	/*
	 * Target function:
	 *
	 * @Bean public Function<String, String> uppercase()
	 */
	@Test
	public void testWithMainAndStartClassAndSpringConfiguration() throws Exception {
		String[] args = new String[] {
				"--spring.cloud.function.location=target/it/bootapp/target/bootapp-0.0.1.BUILD-SNAPSHOT-exec.jar",
				"--spring.cloud.function.function-name=uppercase" };
		ApplicationContext context = SpringApplication.run(FunctionDeployerConfiguration.class, args);
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		Function<Message<byte[]>, Message<byte[]>> function = catalog.lookup("uppercase", "application/json");

		Message<byte[]> result = function
				.apply(MessageBuilder.withPayload("\"bob\"".getBytes(StandardCharsets.UTF_8)).build());
		assertThat(new String(result.getPayload(), StandardCharsets.UTF_8)).isEqualTo("\"BOB\"");
	}

	/*
	 * Target function:
	 *
	 * @Bean public Function<Person, Person> uppercasePerson()
	 */
	@Test
	public void testWithMainAndStartClassAndSpringConfigurationAndTypeConversion() throws Exception {
		String[] args = new String[] {
				"--spring.cloud.function.location=target/it/bootapp/target/bootapp-0.0.1.BUILD-SNAPSHOT-exec.jar",
				"--spring.cloud.function.function-name=uppercasePerson" };

		ApplicationContext context = SpringApplication.run(FunctionDeployerConfiguration.class, args);
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		Function<Message<byte[]>, Message<byte[]>> function = catalog.lookup("uppercasePerson", "application/json");

		Message<byte[]> result = function.apply(
				MessageBuilder.withPayload("{\"name\":\"bob\",\"id\":1}".getBytes(StandardCharsets.UTF_8)).build());
		assertThat(new String(result.getPayload(), StandardCharsets.UTF_8)).isEqualTo("{\"name\":\"BOB\",\"id\":1}");
	}

}
