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

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.adapter.test.aws.AWSCustomRuntime;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
/**
 *
 * @author Oleg Zhurakousky
 */
public class CustomRuntimeEventLoopTest {

	@Test
	public void testDefaultFunctionLookup() throws Exception {
		try (ConfigurableApplicationContext userContext =
				new SpringApplicationBuilder(SingleFunctionConfiguration.class, AWSCustomRuntime.class)
					.web(WebApplicationType.SERVLET)
					.properties("_HANDLER=uppercase", "server.port=0")
					.run()) {

			AWSCustomRuntime aws = userContext.getBean(AWSCustomRuntime.class);
			Message<String> replyMessage = aws.exchange("\"ricky\"");
			assertThat(replyMessage.getHeaders()).containsKey("user-agent");
			assertThat(((String) replyMessage.getHeaders().get("user-agent"))).startsWith("spring-cloud-function");
			assertThat(aws.exchange("\"ricky\"").getPayload()).isEqualTo("\"RICKY\"");
			assertThat(aws.exchange("\"julien\"").getPayload()).isEqualTo("\"JULIEN\"");
			assertThat(aws.exchange("\"bubbles\"").getPayload()).isEqualTo("\"BUBBLES\"");
		}
	}

	@Test
	public void testDefaultFunctionAsComponentLookup() throws Exception {
		try (ConfigurableApplicationContext userContext =
				new SpringApplicationBuilder(PersonFunction.class, AWSCustomRuntime.class)
					.web(WebApplicationType.SERVLET)
					.properties("_HANDLER=personFunction", "server.port=0")
					.run()) {

			AWSCustomRuntime aws = userContext.getBean(AWSCustomRuntime.class);

			assertThat(aws.exchange("\"ricky\"").getPayload()).isEqualTo("{\"name\":\"RICKY\"}");
			assertThat(aws.exchange("\"julien\"").getPayload()).isEqualTo("{\"name\":\"JULIEN\"}");
			assertThat(aws.exchange("\"bubbles\"").getPayload()).isEqualTo("{\"name\":\"BUBBLES\"}");
		}
	}

	@Test
	public void test_HANDLERlookupAndPojoFunction() throws Exception {
		try (ConfigurableApplicationContext userContext =
				new SpringApplicationBuilder(MultipleFunctionConfiguration.class, AWSCustomRuntime.class)
					.web(WebApplicationType.SERVLET)
					.properties("_HANDLER=uppercasePerson", "server.port=0")
					.run()) {

			AWSCustomRuntime aws = userContext.getBean(AWSCustomRuntime.class);

			assertThat(aws.exchange("\"ricky\"").getPayload()).isEqualTo("{\"name\":\"RICKY\"}");
			assertThat(aws.exchange("\"julien\"").getPayload()).isEqualTo("{\"name\":\"JULIEN\"}");
			assertThat(aws.exchange("\"bubbles\"").getPayload()).isEqualTo("{\"name\":\"BUBBLES\"}");
		}
	}

	@Test
	@DirtiesContext
	public void test_definitionLookupAndComposition() throws Exception {
		try (ConfigurableApplicationContext userContext =
				new SpringApplicationBuilder(MultipleFunctionConfiguration.class, AWSCustomRuntime.class)
					.web(WebApplicationType.SERVLET)
					.properties("_HANDLER=toPersonJson|uppercasePerson", "server.port=0")
					.run()) {

			AWSCustomRuntime aws = userContext.getBean(AWSCustomRuntime.class);

			assertThat(aws.exchange("\"ricky\"").getPayload()).isEqualTo("{\"name\":\"RICKY\"}");
			assertThat(aws.exchange("\"julien\"").getPayload()).isEqualTo("{\"name\":\"JULIEN\"}");
			assertThat(aws.exchange("\"bubbles\"").getPayload()).isEqualTo("{\"name\":\"BUBBLES\"}");
		}

	}

	@EnableAutoConfiguration
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
	@Component("personFunction") // need in test explicitly since it is inner class and name wil be `customRuntimeEventLoopTest.PersonFunction`
	public static class PersonFunction implements Function<Person, Person> {

		public PersonFunction() {
			System.out.println();
		}

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
