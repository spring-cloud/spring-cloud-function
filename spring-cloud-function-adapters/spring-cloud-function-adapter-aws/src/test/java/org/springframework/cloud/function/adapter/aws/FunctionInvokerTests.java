/*
 * Copyright 2012-2020 the original author or authors.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class FunctionInvokerTests {

	ObjectMapper mapper = new ObjectMapper();

	String sampleLBEvent = "{" +
			"    \"requestContext\": {" +
			"        \"elb\": {" +
			"            \"targetGroupArn\": \"arn:aws:elasticloadbalancing:region:123456789012:targetgroup/my-target-group/6d0ecf831eec9f09\"" +
			"        }" +
			"    }," +
			"    \"httpMethod\": \"GET\"," +
			"    \"path\": \"/\"," +
			"    \"headers\": {" +
			"        \"accept\": \"text/html,application/xhtml+xml\"," +
			"        \"accept-language\": \"en-US,en;q=0.8\"," +
			"        \"content-type\": \"text/plain\"," +
			"        \"cookie\": \"cookies\"," +
			"        \"host\": \"lambda-846800462-us-east-2.elb.amazonaws.com\"," +
			"        \"user-agent\": \"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6)\"," +
			"        \"x-amzn-trace-id\": \"Root=1-5bdb40ca-556d8b0c50dc66f0511bf520\"," +
			"        \"x-forwarded-for\": \"72.21.198.66\"," +
			"        \"x-forwarded-port\": \"443\"," +
			"        \"x-forwarded-proto\": \"https\"" +
			"    }," +
			"    \"isBase64Encoded\": false," +
			"    \"body\": \"request_body\"" +
			"}";

	String sampleKinesisEvent = "{" +
			"    \"Records\": [" +
			"        {" +
			"            \"kinesis\": {" +
			"                \"kinesisSchemaVersion\": \"1.0\"," +
			"                \"partitionKey\": \"1\"," +
			"                \"sequenceNumber\": \"49590338271490256608559692538361571095921575989136588898\"," +
			"                \"data\": \"SGVsbG8sIHRoaXMgaXMgYSB0ZXN0Lg==\"," +
			"                \"approximateArrivalTimestamp\": 1545084650.987" +
			"            }," +
			"            \"eventSource\": \"aws:kinesis\"," +
			"            \"eventVersion\": \"1.0\"," +
			"            \"eventID\": \"shardId-000000000006:49590338271490256608559692538361571095921575989136588898\"," +
			"            \"eventName\": \"aws:kinesis:record\"," +
			"            \"invokeIdentityArn\": \"arn:aws:iam::123456789012:role/lambda-role\"," +
			"            \"awsRegion\": \"us-east-2\"," +
			"            \"eventSourceARN\": \"arn:aws:kinesis:us-east-2:123456789012:stream/lambda-stream\"" +
			"        }," +
			"        {" +
			"            \"kinesis\": {" +
			"                \"kinesisSchemaVersion\": \"1.0\"," +
			"                \"partitionKey\": \"1\"," +
			"                \"sequenceNumber\": \"49590338271490256608559692540925702759324208523137515618\"," +
			"                \"data\": \"VGhpcyBpcyBvbmx5IGEgdGVzdC4=\"," +
			"                \"approximateArrivalTimestamp\": 1545084711.166" +
			"            }," +
			"            \"eventSource\": \"aws:kinesis\"," +
			"            \"eventVersion\": \"1.0\"," +
			"            \"eventID\": \"shardId-000000000006:49590338271490256608559692540925702759324208523137515618\"," +
			"            \"eventName\": \"aws:kinesis:record\"," +
			"            \"invokeIdentityArn\": \"arn:aws:iam::123456789012:role/lambda-role\"," +
			"            \"awsRegion\": \"us-east-2\"," +
			"            \"eventSourceARN\": \"arn:aws:kinesis:us-east-2:123456789012:stream/lambda-stream\"" +
			"        }" +
			"    ]" +
			"}";

	String apiGatewayEvent = "{\n" +
			"    \"resource\": \"/uppercase2\",\n" +
			"    \"path\": \"/uppercase2\",\n" +
			"    \"httpMethod\": \"POST\",\n" +
			"    \"headers\": {\n" +
			"        \"accept\": \"*/*\",\n" +
			"        \"content-type\": \"application/json\",\n" +
			"        \"Host\": \"fhul32ccy2.execute-api.eu-west-3.amazonaws.com\",\n" +
			"        \"User-Agent\": \"curl/7.54.0\",\n" +
			"        \"X-Amzn-Trace-Id\": \"Root=1-5ece339e-e0595766066d703ec70f1522\",\n" +
			"        \"X-Forwarded-For\": \"90.37.8.133\",\n" +
			"        \"X-Forwarded-Port\": \"443\",\n" +
			"        \"X-Forwarded-Proto\": \"https\"\n" +
			"    },\n" +
			"    \"multiValueHeaders\": {\n" +
			"        \"accept\": [\n" +
			"            \"*/*\"\n" +
			"        ],\n" +
			"        \"content-type\": [\n" +
			"            \"application/json\"\n" +
			"        ],\n" +
			"        \"Host\": [\n" +
			"            \"fhul32ccy2.execute-api.eu-west-3.amazonaws.com\"\n" +
			"        ],\n" +
			"        \"User-Agent\": [\n" +
			"            \"curl/7.54.0\"\n" +
			"        ],\n" +
			"        \"X-Amzn-Trace-Id\": [\n" +
			"            \"Root=1-5ece339e-e0595766066d703ec70f1522\"\n" +
			"        ],\n" +
			"        \"X-Forwarded-For\": [\n" +
			"            \"90.37.8.133\"\n" +
			"        ],\n" +
			"        \"X-Forwarded-Port\": [\n" +
			"            \"443\"\n" +
			"        ],\n" +
			"        \"X-Forwarded-Proto\": [\n" +
			"            \"https\"\n" +
			"        ]\n" +
			"    },\n" +
			"    \"queryStringParameters\": null,\n" +
			"    \"multiValueQueryStringParameters\": null,\n" +
			"    \"pathParameters\": null,\n" +
			"    \"stageVariables\": null,\n" +
			"    \"requestContext\": {\n" +
			"        \"resourceId\": \"qf0io6\",\n" +
			"        \"resourcePath\": \"/uppercase2\",\n" +
			"        \"httpMethod\": \"POST\",\n" +
			"        \"extendedRequestId\": \"NL0A1EokCGYFZOA=\",\n" +
			"        \"requestTime\": \"27/May/2020:09:32:14 +0000\",\n" +
			"        \"path\": \"/test/uppercase2\",\n" +
			"        \"accountId\": \"313369169943\",\n" +
			"        \"protocol\": \"HTTP/1.1\",\n" +
			"        \"stage\": \"test\",\n" +
			"        \"domainPrefix\": \"fhul32ccy2\",\n" +
			"        \"requestTimeEpoch\": 1590571934872,\n" +
			"        \"requestId\": \"b96500aa-f92a-43c3-9360-868ba4053a00\",\n" +
			"        \"identity\": {\n" +
			"            \"cognitoIdentityPoolId\": null,\n" +
			"            \"accountId\": null,\n" +
			"            \"cognitoIdentityId\": null,\n" +
			"            \"caller\": null,\n" +
			"            \"sourceIp\": \"90.37.8.133\",\n" +
			"            \"principalOrgId\": null,\n" +
			"            \"accessKey\": null,\n" +
			"            \"cognitoAuthenticationType\": null,\n" +
			"            \"cognitoAuthenticationProvider\": null,\n" +
			"            \"userArn\": null,\n" +
			"            \"userAgent\": \"curl/7.54.0\",\n" +
			"            \"user\": null\n" +
			"        },\n" +
			"        \"domainName\": \"fhul32ccy2.execute-api.eu-west-3.amazonaws.com\",\n" +
			"        \"apiId\": \"fhul32ccy2\"\n" +
			"    },\n" +
			"    \"body\":\"hello\",\n" +
			"    \"isBase64Encoded\": false\n" +
			"}";

	String apiGatewayEventWithStructuredBody = "{\n" +
			"    \"resource\": \"/uppercase2\",\n" +
			"    \"path\": \"/uppercase2\",\n" +
			"    \"httpMethod\": \"POST\",\n" +
			"    \"headers\": {\n" +
			"        \"accept\": \"*/*\",\n" +
			"        \"content-type\": \"application/json\",\n" +
			"        \"Host\": \"fhul32ccy2.execute-api.eu-west-3.amazonaws.com\",\n" +
			"        \"User-Agent\": \"curl/7.54.0\",\n" +
			"        \"X-Amzn-Trace-Id\": \"Root=1-5ece339e-e0595766066d703ec70f1522\",\n" +
			"        \"X-Forwarded-For\": \"90.37.8.133\",\n" +
			"        \"X-Forwarded-Port\": \"443\",\n" +
			"        \"X-Forwarded-Proto\": \"https\"\n" +
			"    },\n" +
			"    \"multiValueHeaders\": {\n" +
			"        \"accept\": [\n" +
			"            \"*/*\"\n" +
			"        ],\n" +
			"        \"content-type\": [\n" +
			"            \"application/json\"\n" +
			"        ],\n" +
			"        \"Host\": [\n" +
			"            \"fhul32ccy2.execute-api.eu-west-3.amazonaws.com\"\n" +
			"        ],\n" +
			"        \"User-Agent\": [\n" +
			"            \"curl/7.54.0\"\n" +
			"        ],\n" +
			"        \"X-Amzn-Trace-Id\": [\n" +
			"            \"Root=1-5ece339e-e0595766066d703ec70f1522\"\n" +
			"        ],\n" +
			"        \"X-Forwarded-For\": [\n" +
			"            \"90.37.8.133\"\n" +
			"        ],\n" +
			"        \"X-Forwarded-Port\": [\n" +
			"            \"443\"\n" +
			"        ],\n" +
			"        \"X-Forwarded-Proto\": [\n" +
			"            \"https\"\n" +
			"        ]\n" +
			"    },\n" +
			"    \"queryStringParameters\": null,\n" +
			"    \"multiValueQueryStringParameters\": null,\n" +
			"    \"pathParameters\": null,\n" +
			"    \"stageVariables\": null,\n" +
			"    \"requestContext\": {\n" +
			"        \"resourceId\": \"qf0io6\",\n" +
			"        \"resourcePath\": \"/uppercase2\",\n" +
			"        \"httpMethod\": \"POST\",\n" +
			"        \"extendedRequestId\": \"NL0A1EokCGYFZOA=\",\n" +
			"        \"requestTime\": \"27/May/2020:09:32:14 +0000\",\n" +
			"        \"path\": \"/test/uppercase2\",\n" +
			"        \"accountId\": \"313369169943\",\n" +
			"        \"protocol\": \"HTTP/1.1\",\n" +
			"        \"stage\": \"test\",\n" +
			"        \"domainPrefix\": \"fhul32ccy2\",\n" +
			"        \"requestTimeEpoch\": 1590571934872,\n" +
			"        \"requestId\": \"b96500aa-f92a-43c3-9360-868ba4053a00\",\n" +
			"        \"identity\": {\n" +
			"            \"cognitoIdentityPoolId\": null,\n" +
			"            \"accountId\": null,\n" +
			"            \"cognitoIdentityId\": null,\n" +
			"            \"caller\": null,\n" +
			"            \"sourceIp\": \"90.37.8.133\",\n" +
			"            \"principalOrgId\": null,\n" +
			"            \"accessKey\": null,\n" +
			"            \"cognitoAuthenticationType\": null,\n" +
			"            \"cognitoAuthenticationProvider\": null,\n" +
			"            \"userArn\": null,\n" +
			"            \"userAgent\": \"curl/7.54.0\",\n" +
			"            \"user\": null\n" +
			"        },\n" +
			"        \"domainName\": \"fhul32ccy2.execute-api.eu-west-3.amazonaws.com\",\n" +
			"        \"apiId\": \"fhul32ccy2\"\n" +
			"    },\n" +
			"    \"body\":{\"name\":\"Jim Lahey\"},\n" +
			"    \"isBase64Encoded\": false\n" +
			"}";

	@Test
	public void testKinesisStringEvent() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			System.setProperty("MAIN_CLASS", KinesisConfiguration.class.getName());
			System.setProperty("spring.cloud.function.definition", "echoString");
			FunctionInvoker invoker = new FunctionInvoker();

			InputStream targetStream = new ByteArrayInputStream(this.sampleKinesisEvent.getBytes());
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			invoker.handleRequest(targetStream, output, null);
		});
	}

	@Test
	public void testKinesisEvent() throws Exception {
		System.setProperty("MAIN_CLASS", KinesisConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputKinesisEvent");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.sampleKinesisEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("49590338271490256608559692538361571095921575989136588898");
	}

	@Test
	public void testKinesisEventAsMessage() throws Exception {
		System.setProperty("MAIN_CLASS", KinesisConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputKinesisEventAsMessage");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.sampleKinesisEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("49590338271490256608559692538361571095921575989136588898");
	}

	@Test
	public void testKinesisEventAsMap() throws Exception {
		System.setProperty("MAIN_CLASS", KinesisConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputKinesisEventAsMap");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.sampleKinesisEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).contains("49590338271490256608559692538361571095921575989136588898");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayStringEventBody() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "uppercase");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);
		ObjectMapper mapper = new ObjectMapper();
		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("HELLO");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayMapEventBody() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "uppercasePojo");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEventWithStructuredBody.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat(result.get("body")).isEqualTo("JIM LAHEY");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayEvent() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputApiEvent");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		System.out.println(result);
		assertThat(result.get("body")).isEqualTo("hello");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayEventAsMessage() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputApiEventAsMessage");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		System.out.println(result);
		assertThat(result.get("body")).isEqualTo("hello");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testApiGatewayEventAsMap() throws Exception {
		System.setProperty("MAIN_CLASS", ApiGatewayConfiguration.class.getName());
		System.setProperty("spring.cloud.function.definition", "inputApiEventAsMap");
		FunctionInvoker invoker = new FunctionInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);

		Map result = mapper.readValue(output.toByteArray(), Map.class);
		System.out.println(result);
		assertThat(result.get("body")).isEqualTo("hello");
	}

	@EnableAutoConfiguration
	@Configuration
	public static class KinesisConfiguration {
		@Bean
		public Function<String, String> echoString() {
			return v -> v;
		}

		@Bean
		public Function<KinesisEvent, String> inputKinesisEvent() {
			return v -> {
				System.out.println("Received: " + v);
				return v.toString();
			};
		}

		@Bean
		public Function<Message<KinesisEvent>, String> inputKinesisEventAsMessage() {
			return v -> {
				System.out.println("Received: " + v);
				return v.toString();
			};
		}

		@Bean
		public Function<Map<String, Object>, String> inputKinesisEventAsMap() {
			return v -> {
				System.out.println("Received: " + v);
				return v.toString();
			};
		}
	}

	@EnableAutoConfiguration
	@Configuration
	public static class ApiGatewayConfiguration {
		@Bean
		public Function<String, String> uppercase() {
			return v -> v.toUpperCase();
		}

		@Bean
		public Function<Person, String> uppercasePojo() {
			return v -> v.getName().toUpperCase();
		}

		@Bean
		public Function<APIGatewayProxyRequestEvent, String> inputApiEvent() {
			return v -> {
				return v.getBody();
			};
		}

		@Bean
		public Function<Message<APIGatewayProxyRequestEvent>, String> inputApiEventAsMessage() {
			return v -> {
				return v.getPayload().getBody();
			};
		}

		@Bean
		public Function<Map<String, Object>, String> inputApiEventAsMap() {
			return v -> {
				String body = (String) v.get("body");
				return body;
			};
		}
	}

	public static class Person {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
