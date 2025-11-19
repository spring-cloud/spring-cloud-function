/*
 * Copyright 2021-present the original author or authors.
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

import java.util.Locale;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

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
 * @author Oleg Zhurakousky
 */
public class CustomRuntimeEventLoopTest {

	private String API_EVENT = """
			{
			    "version": "1.0",
			    "resource": "$default",
			    "path": "/question",
			    "httpMethod": "POST",
			    "headers": {
			        "Content-Length": "40",
			        "Content-Type": "application/json",
			        "Host": "emcdxu5ijj.execute-api.us-east-2.amazonaws.com",
			        "User-Agent": "curl/7.88.1",
			        "X-Amzn-Trace-Id": "Root=1-64ad9787-4c89d5af7607eb9e522e01d5",
			        "X-Forwarded-For": "109.210.252.44",
			        "X-Forwarded-Port": "443",
			        "X-Forwarded-Proto": "https",
			        "accept": "*/*"
			    },
			    "multiValueHeaders": {
			        "Content-Length": [
			            "40"
			        ],
			        "Content-Type": [
			            "application/json"
			        ],
			        "Host": [
			            "emcdxu5ijj.execute-api.us-east-2.amazonaws.com"
			        ],
			        "User-Agent": [
			            "curl/7.88.1"
			        ],
			        "X-Amzn-Trace-Id": [
			            "Root=1-64ad9787-4c89d5af7607eb9e522e01d5"
			        ],
			        "X-Forwarded-For": [
			            "109.210.252.44"
			        ],
			        "X-Forwarded-Port": [
			            "443"
			        ],
			        "X-Forwarded-Proto": [
			            "https"
			        ],
			        "accept": [
			            "*/*"
			        ]
			    },
			    "queryStringParameters": null,
			    "multiValueQueryStringParameters": null,
			    "requestContext": {
			        "accountId": "313369169943",
			        "apiId": "emcdxu5ijj",
			        "domainName": "emcdxu5ijj.execute-api.us-east-2.amazonaws.com",
			        "domainPrefix": "emcdxu5ijj",
			        "extendedRequestId": "H6SdPgXtiYcEP1w=",
			        "httpMethod": "POST",
			        "identity": {
			            "accessKey": null,
			            "accountId": null,
			            "caller": null,
			            "cognitoAmr": null,
			            "cognitoAuthenticationProvider": null,
			            "cognitoAuthenticationType": null,
			            "cognitoIdentityId": null,
			            "cognitoIdentityPoolId": null,
			            "principalOrgId": null,
			            "sourceIp": "109.210.252.44",
			            "user": null,
			            "userAgent": "curl/7.88.1",
			            "userArn": null
			        },
			        "path": "/question",
			        "protocol": "HTTP/1.1",
			        "requestId": "H6SdPgXtiYcEP1w=",
			        "requestTime": "11/Jul/2023:17:55:19 +0000",
			        "requestTimeEpoch": 1689098119662,
			        "resourceId": "$default",
			        "resourcePath": "$default",
			        "stage": "$default"
			    },
			    "pathParameters": null,
			    "stageVariables": null,
			    "body": "[{\\"latitude\\": 41.34, \\"longitude\\": 2.78},{\\"latitude\\": 43.24, \\"longitude\\": 3.78}]",
			    "isBase64Encoded": false
			}""";

	@Test
	public void testDefaultFunctionLookup() throws Exception {
		testDefaultFunctionLookup("uppercase", SingleFunctionConfiguration.class);
	}

	@Test
	public void testDefaultFunctionLookupReactive() throws Exception {
		testDefaultFunctionLookup("uppercase", SingleFunctionConfigurationReactive.class);
	}

	private void testDefaultFunctionLookup(String handler, Class<?> context) throws Exception {
		try (ConfigurableApplicationContext userContext = new SpringApplicationBuilder(context, AWSCustomRuntime.class)
			.web(WebApplicationType.SERVLET)
			.properties("_HANDLER=" + handler, "server.port=0")
			.run()) {

			AWSCustomRuntime aws = userContext.getBean(AWSCustomRuntime.class);
			Message<String> replyMessage = aws.exchange("\"ricky\"");
			assertThat(replyMessage.getHeaders()).containsKey("User-Agent");
			assertThat(((String) replyMessage.getHeaders().get("User-Agent"))).startsWith("spring-cloud-function");
			assertThat(aws.exchange("\"ricky\"").getPayload()).isEqualTo("\"RICKY\"");
			assertThat(aws.exchange("\"julien\"").getPayload()).isEqualTo("\"JULIEN\"");
			assertThat(aws.exchange("\"bubbles\"").getPayload()).isEqualTo("\"BUBBLES\"");
		}
	}

	// @Test
	public void testDefaultFunctionAsComponentLookup() throws Exception {
		try (ConfigurableApplicationContext userContext = new SpringApplicationBuilder(PersonFunction.class,
				AWSCustomRuntime.class)
			.web(WebApplicationType.SERVLET)
			.properties("_HANDLER=personFunction", "server.port=0")
			.run()) {

			AWSCustomRuntime aws = userContext.getBean(AWSCustomRuntime.class);

			assertThat(aws.exchange("\"ricky\"").getPayload()).isEqualTo("{\"name\":\"RICKY\"}");
			assertThat(aws.exchange("\"julien\"").getPayload()).isEqualTo("{\"name\":\"JULIEN\"}");
			assertThat(aws.exchange("\"bubbles\"").getPayload()).isEqualTo("{\"name\":\"BUBBLES\"}");
		}
	}

	// @Test
	public void test_HANDLERlookupAndPojoFunction() throws Exception {
		try (ConfigurableApplicationContext userContext = new SpringApplicationBuilder(
				MultipleFunctionConfiguration.class, AWSCustomRuntime.class)
			.web(WebApplicationType.SERVLET)
			.properties("_HANDLER=uppercasePerson", "server.port=0")
			.run()) {

			AWSCustomRuntime aws = userContext.getBean(AWSCustomRuntime.class);

			aws.exchange("\"ricky\"");
			assertThat(aws.exchange("\"ricky\"").getPayload()).isEqualTo("{\"name\":\"RICKY\"}");
			assertThat(aws.exchange("\"julien\"").getPayload()).isEqualTo("{\"name\":\"JULIEN\"}");
			assertThat(aws.exchange("\"bubbles\"").getPayload()).isEqualTo("{\"name\":\"BUBBLES\"}");
		}
	}

	@Test
	public void test_HANDLERWithApiGatewayRequestAndFlux() throws Exception {
		try (ConfigurableApplicationContext userContext = new SpringApplicationBuilder(
				MultipleFunctionConfiguration.class, AWSCustomRuntime.class)
			.web(WebApplicationType.SERVLET)
			.properties("_HANDLER=echoFlux", "server.port=0")
			.run()) {

			AWSCustomRuntime aws = userContext.getBean(AWSCustomRuntime.class);
			String response = aws.exchange(API_EVENT).getPayload();
			assertThat(response).contains("{\\\"latitude\\\":2.78,\\\"longitude\\\":41.34}");
			assertThat(response).contains("{\\\"latitude\\\":3.78,\\\"longitude\\\":43.24}");
		}
	}

	@Test
	@DirtiesContext
	public void test_definitionLookupAndComposition() throws Exception {
		try (ConfigurableApplicationContext userContext = new SpringApplicationBuilder(
				MultipleFunctionConfiguration.class, AWSCustomRuntime.class)
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
			return v -> v.toUpperCase(Locale.ROOT);
		}

	}

	@EnableAutoConfiguration
	protected static class SingleFunctionConfigurationReactive {

		@Bean
		public Function<Flux<String>, Flux<String>> uppercase() {
			return v -> v.map(String::toUpperCase);
		}

	}

	@EnableAutoConfiguration
	@Configuration(proxyBeanMethods = false)
	protected static class MultipleFunctionConfiguration {

		@Bean
		public Function<String, String> uppercase() {
			return v -> v.toUpperCase(Locale.ROOT);
		}

		@Bean
		public Function<String, String> toPersonJson() {
			return v -> "{\"name\":\"" + v + "\"}";
		}

		@Bean
		public Function<Person, Person> uppercasePerson() {
			return p -> new Person(p.getName().toUpperCase(Locale.ROOT));
		}

		@Bean
		public Function<Flux<GeoLocation>, Flux<GeoLocation>> echoFlux() {
			return flux -> flux.map(g -> {
				return new GeoLocation(g.longitude(), g.latitude());
			});
		}

	}

	@EnableAutoConfiguration
	@Component("personFunction") // need in test explicitly since it is inner class and
									// name wil be
									// `customRuntimeEventLoopTest.PersonFunction`
	public static class PersonFunction implements Function<Person, Person> {

		public PersonFunction() {
			System.out.println();
		}

		@Override
		public Person apply(Person input) {
			return new Person(input.getName().toUpperCase(Locale.ROOT));
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

	public record GeoLocation(Float latitude, Float longitude) {
	}

}
