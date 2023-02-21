/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.cloud.function.adapter.aws.web;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.function.adapter.aws.TestContext;
import org.springframework.cloud.function.json.JacksonMapper;


public class WebProxyInvokerTests {

	static String apiGatewayEvent = "{\n" +
			"    \"resource\": \"/pets\",\n" +
			"    \"path\": \"/pets/64f56d94-a059-4111-9eeb-ee0c994b1ba8\",\n" +
			"    \"httpMethod\": \"GET\",\n" +
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
			"        \"resourcePath\": \"/pets\",\n" +
			"        \"httpMethod\": \"GET\",\n" +
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
			"    \"body\":\"\",\n" +
			"    \"isBase64Encoded\": false\n" +
			"}";

	@Test
	public void testApiGatewayProxy() throws Exception {
		System.setProperty("MAIN_CLASS", PetStoreSpringAppConfig.class.getName());
		WebProxyInvoker invoker = new WebProxyInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, new TestContext());

		JacksonMapper mapper = new JacksonMapper(new ObjectMapper());
		System.out.println("RESULT: =======> " + new String(output.toByteArray()));
		Map result = mapper.fromJson(output.toByteArray(), Map.class);
		System.out.println(result);
	}
}
