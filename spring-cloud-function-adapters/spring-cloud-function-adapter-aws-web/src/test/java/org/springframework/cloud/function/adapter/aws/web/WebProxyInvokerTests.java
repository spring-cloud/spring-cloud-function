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
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.function.serverless.web.ProxyHttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Oleg Zhurakousky
 */
public class WebProxyInvokerTests {

	static String API_GATEWAY_EVENT = "{\n"
			+ "    \"version\": \"1.0\",\n"
			+ "    \"resource\": \"$default\",\n"
			+ "    \"path\": \"/pets\",\n"
			+ "    \"httpMethod\": \"POST\",\n"
			+ "    \"headers\": {\n"
			+ "        \"Content-Length\": \"45\",\n"
			+ "        \"Content-Type\": \"application/json\",\n"
			+ "        \"Host\": \"i76bfhczs0.execute-api.eu-west-3.amazonaws.com\",\n"
			+ "        \"User-Agent\": \"curl/7.79.1\",\n"
			+ "        \"X-Amzn-Trace-Id\": \"Root=1-64087690-2151375b219d3ba3389ea84e\",\n"
			+ "        \"X-Forwarded-For\": \"109.210.252.44\",\n"
			+ "        \"X-Forwarded-Port\": \"443\",\n"
			+ "        \"X-Forwarded-Proto\": \"https\",\n"
			+ "        \"accept\": \"*/*\"\n"
			+ "    },\n"
			+ "    \"multiValueHeaders\": {\n"
			+ "        \"Content-Length\": [\n"
			+ "            \"45\"\n"
			+ "        ],\n"
			+ "        \"Content-Type\": [\n"
			+ "            \"application/json\"\n"
			+ "        ],\n"
			+ "        \"Host\": [\n"
			+ "            \"i76bfhczs0.execute-api.eu-west-3.amazonaws.com\"\n"
			+ "        ],\n"
			+ "        \"User-Agent\": [\n"
			+ "            \"curl/7.79.1\"\n"
			+ "        ],\n"
			+ "        \"X-Amzn-Trace-Id\": [\n"
			+ "            \"Root=1-64087690-2151375b219d3ba3389ea84e\"\n"
			+ "        ],\n"
			+ "        \"X-Forwarded-For\": [\n"
			+ "            \"109.210.252.44\"\n"
			+ "        ],\n"
			+ "        \"X-Forwarded-Port\": [\n"
			+ "            \"443\"\n"
			+ "        ],\n"
			+ "        \"X-Forwarded-Proto\": [\n"
			+ "            \"https\"\n"
			+ "        ],\n"
			+ "        \"accept\": [\n"
			+ "            \"*/*\"\n"
			+ "        ]\n"
			+ "    },\n"
			+ "    \"queryStringParameters\": {\n"
			+ "        \"abc\": \"xyz\",\n"
			+ "        \"foo\": \"baz\"\n"
			+ "    },\n"
			+ "    \"multiValueQueryStringParameters\": {\n"
			+ "        \"abc\": [\n"
			+ "            \"xyz\"\n"
			+ "        ],\n"
			+ "        \"foo\": [\n"
			+ "            \"bar\",\n"
			+ "            \"baz\"\n"
			+ "        ]\n"
			+ "    },\n"
			+ "    \"requestContext\": {\n"
			+ "        \"accountId\": \"313369169943\",\n"
			+ "        \"apiId\": \"i76bfhczs0\",\n"
			+ "        \"domainName\": \"i76bfhczs0.execute-api.eu-west-3.amazonaws.com\",\n"
			+ "        \"domainPrefix\": \"i76bfhczs0\",\n"
			+ "        \"extendedRequestId\": \"Bdd2ngt5iGYEMIg=\",\n"
			+ "        \"httpMethod\": \"POST\",\n"
			+ "        \"identity\": {\n"
			+ "            \"accessKey\": null,\n"
			+ "            \"accountId\": null,\n"
			+ "            \"caller\": null,\n"
			+ "            \"cognitoAmr\": null,\n"
			+ "            \"cognitoAuthenticationProvider\": null,\n"
			+ "            \"cognitoAuthenticationType\": null,\n"
			+ "            \"cognitoIdentityId\": null,\n"
			+ "            \"cognitoIdentityPoolId\": null,\n"
			+ "            \"principalOrgId\": null,\n"
			+ "            \"sourceIp\": \"109.210.252.44\",\n"
			+ "            \"user\": null,\n"
			+ "            \"userAgent\": \"curl/7.79.1\",\n"
			+ "            \"userArn\": null\n"
			+ "        },\n"
			+ "        \"path\": \"/pets\",\n"
			+ "        \"protocol\": \"HTTP/1.1\",\n"
			+ "        \"requestId\": \"Bdd2ngt5iGYEMIg=\",\n"
			+ "        \"requestTime\": \"08/Mar/2023:11:50:40 +0000\",\n"
			+ "        \"requestTimeEpoch\": 1678276240455,\n"
			+ "        \"resourceId\": \"$default\",\n"
			+ "        \"resourcePath\": \"$default\",\n"
			+ "        \"stage\": \"$default\"\n"
			+ "    },\n"
			+ "    \"pathParameters\": null,\n"
			+ "    \"stageVariables\": null,\n"
			+ "    \"body\": \"{\\\"id\\\":\\\"123\\\",\\\"breed\\\":\\\"Datsun\\\",\\\"name\\\":\\\"Donald\\\"}\",\n"
			+ "    \"isBase64Encoded\": false\n"
			+ "}";




	@Test
	public void validateHttpServletRequestConstruction() throws Exception {
		System.setProperty("MAIN_CLASS", PetStoreSpringAppConfig.class.getName());
		WebProxyInvoker invoker = new WebProxyInvoker();
		InputStream targetStream = new ByteArrayInputStream(API_GATEWAY_EVENT.getBytes());
		Method prepareRequest  = ReflectionUtils.findMethod(WebProxyInvoker.class, "prepareRequest", InputStream.class);
		prepareRequest.setAccessible(true);
		ProxyHttpServletRequest request = (ProxyHttpServletRequest) prepareRequest.invoke(invoker, targetStream);

		assertThat(request.getContentType()).isEqualTo("application/json");
		assertThat(request.getParameterValues("foo").length).isEqualTo(2);
		assertThat(request.getParameterValues("foo")[0]).isEqualTo("bar");
		assertThat(request.getParameterValues("abc").length).isEqualTo(1);
		assertThat(request.getParameterValues("abc")[0]).isEqualTo("xyz");
		assertThat(request.getHeaders(HttpHeaders.CONTENT_TYPE).nextElement()).isEqualTo("application/json");
		assertThat(request.getContentAsString()).isEqualTo("{\"id\":\"123\",\"breed\":\"Datsun\",\"name\":\"Donald\"}");
		assertThat(request.getContentLength()).isEqualTo(45);
		assertThat(request.getMethod()).isEqualTo("POST");
	}

	@Test
	public void testApiGatewayProxy() throws Exception {
		System.setProperty("MAIN_CLASS", PetStoreSpringAppConfig.class.getName());
		WebProxyInvoker invoker = new WebProxyInvoker();

		InputStream targetStream = new ByteArrayInputStream(API_GATEWAY_EVENT.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output);

		ObjectMapper mapper = new ObjectMapper();
		Map result = mapper.readValue(output.toByteArray(), Map.class);
		assertThat((boolean) result.get("isBase64Encoded")).isFalse();
		assertThat(((Map<String, List<String>>) result.get("multiValueHeaders")).get("Content-Type").get(0)).isEqualTo("application/json");
		assertThat(result.get("statusCode")).isEqualTo(200);
		Pet pet = mapper.readValue((String) result.get("body"), Pet.class);
		assertThat(pet.getName()).isEqualTo("Donald");
	}
}
