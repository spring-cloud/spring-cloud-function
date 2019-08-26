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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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
				"--spring.cloud.function.location=target/it/bootjar/target/bootjar-1.0.0.RELEASE-exec.jar",
				"--spring.cloud.function.function-class=function.example.UpperCaseFunction" };

		ApplicationContext context = SpringApplication.run(DeployerApplication.class, args);
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		Function<String, String> function = catalog.lookup("upperCaseFunction");

		assertThat(function.apply("bob")).isEqualTo("BOB");
		assertThat(function.apply("stacy")).isEqualTo("STACY");

		Function<Flux<String>, Flux<String>> functionAsFlux = catalog.lookup("upperCaseFunction");

		List<String> results = functionAsFlux.apply(Flux.just("bob", "stacy")).collectList().block();
		assertThat(results.get(0)).isEqualTo("BOB");
		assertThat(results.get(1)).isEqualTo("STACY");
	}

	@Test
	public void testWithSimplestJar() throws Exception {
		String[] args = new String[] {
				"--spring.cloud.function.location=target/it/simplestjar/target/simplestjar-1.0.0.RELEASE.jar",
				"--spring.cloud.function.function-class=function.example.UpperCaseFunction" };

		ApplicationContext context = SpringApplication.run(DeployerApplication.class, args);
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		Function<String, String> function = catalog.lookup("upperCaseFunction");

		assertThat(function.apply("bob")).isEqualTo("BOB");
		assertThat(function.apply("stacy")).isEqualTo("STACY");

		Function<Flux<String>, Flux<String>> functionAsFlux = catalog.lookup("upperCaseFunction");

		List<String> results = functionAsFlux.apply(Flux.just("bob", "stacy")).collectList().block();
		assertThat(results.get(0)).isEqualTo("BOB");
		assertThat(results.get(1)).isEqualTo("STACY");
	}

	@Test
	public void testWithSimplestJarExploaded() throws Exception {
		String[] args = new String[] {
				"--spring.cloud.function.location=target/it/simplestjar/target/classes",
				"--spring.cloud.function.function-class=function.example.UpperCaseFunction" };

		ApplicationContext context = SpringApplication.run(DeployerApplication.class, args);
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
				"--spring.cloud.function.location=target/it/bootjarnostart/target/bootjarnostart-1.0.0.RELEASE-exec.jar",
				"--spring.cloud.function.function-class=function.example.UpperCaseFunction" };

		ApplicationContext context = SpringApplication.run(DeployerApplication.class, args);
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
	 *
	 * Function class is discovered via 'Function-Class` manifest entry
	 */
	@Test
	public void testNoMainAndNoStartClassAndNoSpringConfigurationDiscoverClassFromManifest() throws Exception {
		String[] args = new String[] {
				"--spring.cloud.function.location=target/it/bootjarnostart/target/bootjarnostart-1.0.0.RELEASE-exec.jar" };

		ApplicationContext context = SpringApplication.run(DeployerApplication.class, args);
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
				"--spring.cloud.function.location=target/it/bootapp/target/bootapp-1.0.0.RELEASE-exec.jar",
				"--spring.cloud.function.function-name=uppercase" };
		ApplicationContext context = SpringApplication.run(DeployerApplication.class, args);
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		Function<Message<byte[]>, Message<byte[]>> function = catalog.lookup("uppercase", "application/json");

		Message<byte[]> result = function
				.apply(MessageBuilder.withPayload("\"bob\"".getBytes(StandardCharsets.UTF_8)).build());
		assertThat(new String(result.getPayload(), StandardCharsets.UTF_8)).isEqualTo("\"BOB\"");
	}

	@Test
	public void testWithLegacyProperties() throws Exception {
		String[] args = new String[] {
				"--function.location=target/it/bootapp/target/bootapp-1.0.0.RELEASE-exec.jar",
				"--function.name=uppercase" };
		ApplicationContext context = SpringApplication.run(DeployerApplication.class, args);
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		Function<Message<byte[]>, Message<byte[]>> function = catalog.lookup("uppercase", "application/json");

		Message<byte[]> result = function
				.apply(MessageBuilder.withPayload("\"bob\"".getBytes(StandardCharsets.UTF_8)).build());
		assertThat(new String(result.getPayload(), StandardCharsets.UTF_8)).isEqualTo("\"BOB\"");
	}

	/*
	 * Same as above but:
	 * Given that Java 11 does not include 'javax' packages, this test simply validates that
	 * the delegation will be made to archive loader where it is available
	 */
	@Test
	public void testWithMainAndStartClassAndSpringConfigurationJavax() throws Exception {
		String[] args = new String[] {
				"--spring.cloud.function.location=target/it/bootapp-with-javax/target/bootapp-with-javax-1.0.0.RELEASE-exec.jar",
				"--spring.cloud.function.function-name=uppercase" };
		ApplicationContext context = SpringApplication.run(DeployerApplication.class, args);
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		Function<Message<byte[]>, Message<byte[]>> function = catalog.lookup("uppercase", "application/json");

		Message<byte[]> result = function
				.apply(MessageBuilder.withPayload("\"foo@bar.com\"".getBytes(StandardCharsets.UTF_8)).build());
		assertThat(new String(result.getPayload(), StandardCharsets.UTF_8)).isEqualTo("\"FOO@BAR.COM\"");
	}

	/*
	 * Target function:
	 *
	 * @Bean public Function<String, String> uppercase()
	 *
	 * this contains SCF on classpath
	 */
	@Test
	public void testWithMainAndStartClassAndSpringConfigurationAndSCFOnClasspath() throws Exception {
		String[] args = new String[] {
				"--spring.cloud.function.location=target/it/bootapp-with-scf/target/bootapp-with-scf-1.0.0.RELEASE-exec.jar",
				"--spring.cloud.function.function-name=uppercase" };
		ApplicationContext context = SpringApplication.run(DeployerApplication.class, args);
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
				"--spring.cloud.function.location=target/it/bootapp/target/bootapp-1.0.0.RELEASE-exec.jar",
				"--spring.cloud.function.function-name=uppercasePerson" };

		ApplicationContext context = SpringApplication.run(DeployerApplication.class, args);
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		Function<Message<byte[]>, Message<byte[]>> function = catalog.lookup("uppercasePerson", "application/json");

		Message<byte[]> result = function.apply(
				MessageBuilder.withPayload("{\"name\":\"bob\",\"id\":1}".getBytes(StandardCharsets.UTF_8)).build());
		assertThat(new String(result.getPayload(), StandardCharsets.UTF_8)).isEqualTo("{\"name\":\"BOB\",\"id\":1}");
	}

	/*
	 * Target Function
	 *
	 * @Bean Function<Tuple2<Flux<String>, Flux<Integer>>, Tuple2<Flux<Double>, Flux<String>>>
	 */
	@Test
	public void testBootAppWithMultipleInputOutput() {
		String[] args = new String[] {
				"--spring.cloud.function.location=target/it/bootapp-multi/target/bootapp-multi-1.0.0.RELEASE-exec.jar",
				"--spring.cloud.function.function-name=fn"
		};
		ApplicationContext context = SpringApplication.run(DeployerApplication.class, args);
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);

		Function<Tuple2<Flux<Message<byte[]>>, Flux<Message<byte[]>>>, Tuple2<Flux<Message<byte[]>>, Flux<Message<byte[]>>>> function =
				catalog.lookup("fn", "application/json", "application/json");

		Message<byte[]> msg1 = MessageBuilder.withPayload("\"one\"".getBytes()).build();
		Message<byte[]> msg2 = MessageBuilder.withPayload("\"two\"".getBytes()).build();
		Flux<Message<byte[]>> inputOne = Flux.just(msg1, msg2);

		Message<byte[]> msgInt1 = MessageBuilder.withPayload("\"1\"".getBytes()).build();
		Message<byte[]> msgInt2 = MessageBuilder.withPayload("\"2\"".getBytes()).build();
		Flux<Message<byte[]>> inputTwo = Flux.just(msgInt1, msgInt2);

		Tuple2<Flux<Message<byte[]>>, Flux<Message<byte[]>>> result = function.apply(Tuples.of(inputOne, inputTwo));
		List<String> result1 = new ArrayList<>();
		List<String> result2 = new ArrayList<>();
		result.getT1().subscribe(message -> {
			result1.add(new String(message.getPayload()));
		});
		result.getT2().subscribe(message -> {
			result2.add(new String(message.getPayload()));
		});

		assertThat(result1.get(0)).isEqualTo("1.5");
		assertThat(result1.get(1)).isEqualTo("2.0");

		assertThat(result2.get(0)).isEqualTo("\"one\"");
		assertThat(result2.get(1)).isEqualTo("\"two\"");
	}

	/*
	 * Target Function
	 *
	 * Function<Tuple2<Flux<String>, Flux<Integer>>, Tuple2<Flux<Double>, Flux<String>>>
	 */
	@Test
	public void testBootJarWithMultipleInputOutput() {
		String[] args = new String[] {
				"--spring.cloud.function.location=target/it/bootjar-multi/target/bootjar-multi-1.0.0.RELEASE-exec.jar",
				"--spring.cloud.function.function-class=function.example.Repeater"
		};
		ApplicationContext context = SpringApplication.run(DeployerApplication.class, args);
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);

		Function<Tuple2<Flux<Message<byte[]>>, Flux<Message<byte[]>>>, Tuple2<Flux<Message<byte[]>>, Flux<Message<byte[]>>>> function =
				catalog.lookup("repeater", "application/json", "application/json");

		Message<byte[]> msg1 = MessageBuilder.withPayload("\"one\"".getBytes()).build();
		Message<byte[]> msg2 = MessageBuilder.withPayload("\"two\"".getBytes()).build();
		Flux<Message<byte[]>> inputOne = Flux.just(msg1, msg2);

		Message<byte[]> msgInt1 = MessageBuilder.withPayload("\"1\"".getBytes()).build();
		Message<byte[]> msgInt2 = MessageBuilder.withPayload("\"2\"".getBytes()).build();
		Flux<Message<byte[]>> inputTwo = Flux.just(msgInt1, msgInt2);

		Tuple2<Flux<Message<byte[]>>, Flux<Message<byte[]>>> result = function.apply(Tuples.of(inputOne, inputTwo));
		List<String> result1 = new ArrayList<>();
		List<String> result2 = new ArrayList<>();
		result.getT1().subscribe(message -> {
			result1.add(new String(message.getPayload()));
		});
		result.getT2().subscribe(message -> {
			result2.add(new String(message.getPayload()));
		});

		assertThat(result1.get(0)).isEqualTo("\"one\"");
		assertThat(result1.get(1)).isEqualTo("\"two\"");

		assertThat(result2.get(0)).isEqualTo("3");
		assertThat(result2.get(1)).isEqualTo("2");
	}

	@SpringBootApplication(proxyBeanMethods = false)
	private static class DeployerApplication {
	}
}
