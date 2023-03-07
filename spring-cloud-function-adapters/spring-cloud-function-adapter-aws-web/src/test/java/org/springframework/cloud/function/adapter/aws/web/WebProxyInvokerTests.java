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
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.function.serverless.web.ProxyHttpServletRequest;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Oleg Zhurakousky
 */
public class WebProxyInvokerTests {

	static String apiGatewayEvent = "{\n"
			+ "    \"version\": \"1.0\",\n"
			+ "    \"resource\": \"$default\",\n"
			+ "    \"path\": \"/pets\",\n"
			+ "    \"httpMethod\": \"GET\",\n"
			+ "    \"headers\": {\n"
			+ "        \"Content-Length\": \"0\",\n"
			+ "        \"content-type\": \"application/json\",\n"
			+ "        \"Host\": \"i76bfhczs0.execute-api.eu-west-3.amazonaws.com\",\n"
			+ "        \"User-Agent\": \"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36\",\n"
			+ "        \"X-Amzn-Trace-Id\": \"Root=1-640739ff-4face0f32be123794d1e8a10\",\n"
			+ "        \"X-Forwarded-For\": \"109.210.252.44\",\n"
			+ "        \"X-Forwarded-Port\": \"443\",\n"
			+ "        \"X-Forwarded-Proto\": \"https\",\n"
			+ "        \"accept\": \"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9\",\n"
			+ "        \"accept-encoding\": \"gzip, deflate, br\",\n"
			+ "        \"accept-language\": \"en-US,en;q=0.9,ru;q=0.8,uk;q=0.7\",\n"
			+ "        \"sec-ch-ua\": \"\\\"Not?A_Brand\\\";v=\\\"8\\\", \\\"Chromium\\\";v=\\\"108\\\", \\\"Google Chrome\\\";v=\\\"108\\\"\",\n"
			+ "        \"sec-ch-ua-mobile\": \"?0\",\n"
			+ "        \"sec-ch-ua-platform\": \"\\\"macOS\\\"\",\n"
			+ "        \"sec-fetch-dest\": \"document\",\n"
			+ "        \"sec-fetch-mode\": \"navigate\",\n"
			+ "        \"sec-fetch-site\": \"none\",\n"
			+ "        \"sec-fetch-user\": \"?1\",\n"
			+ "        \"upgrade-insecure-requests\": \"1\"\n"
			+ "    },\n"
			+ "    \"multiValueHeaders\": {\n"
			+ "        \"Content-Length\": [\n"
			+ "            \"0\"\n"
			+ "        ],\n"
			+ "        \"content-type\": [\n"
			+ "            \"application/json\"\n"
			+ "        ],\n"
			+ "        \"Host\": [\n"
			+ "            \"i76bfhczs0.execute-api.eu-west-3.amazonaws.com\"\n"
			+ "        ],\n"
			+ "        \"User-Agent\": [\n"
			+ "            \"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36\"\n"
			+ "        ],\n"
			+ "        \"X-Amzn-Trace-Id\": [\n"
			+ "            \"Root=1-640739ff-4face0f32be123794d1e8a10\"\n"
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
			+ "            \"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9\"\n"
			+ "        ],\n"
			+ "        \"accept-encoding\": [\n"
			+ "            \"gzip, deflate, br\"\n"
			+ "        ],\n"
			+ "        \"accept-language\": [\n"
			+ "            \"en-US,en;q=0.9,ru;q=0.8,uk;q=0.7\"\n"
			+ "        ],\n"
			+ "        \"sec-ch-ua\": [\n"
			+ "            \"\\\"Not?A_Brand\\\";v=\\\"8\\\", \\\"Chromium\\\";v=\\\"108\\\", \\\"Google Chrome\\\";v=\\\"108\\\"\"\n"
			+ "        ],\n"
			+ "        \"sec-ch-ua-mobile\": [\n"
			+ "            \"?0\"\n"
			+ "        ],\n"
			+ "        \"sec-ch-ua-platform\": [\n"
			+ "            \"\\\"macOS\\\"\"\n"
			+ "        ],\n"
			+ "        \"sec-fetch-dest\": [\n"
			+ "            \"document\"\n"
			+ "        ],\n"
			+ "        \"sec-fetch-mode\": [\n"
			+ "            \"navigate\"\n"
			+ "        ],\n"
			+ "        \"sec-fetch-site\": [\n"
			+ "            \"none\"\n"
			+ "        ],\n"
			+ "        \"sec-fetch-user\": [\n"
			+ "            \"?1\"\n"
			+ "        ],\n"
			+ "        \"upgrade-insecure-requests\": [\n"
			+ "            \"1\"\n"
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
			+ "        \"extendedRequestId\": \"BaYAChmeiGYEJyQ=\",\n"
			+ "        \"httpMethod\": \"GET\",\n"
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
			+ "            \"userAgent\": \"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36\",\n"
			+ "            \"userArn\": null\n"
			+ "        },\n"
			+ "        \"path\": \"/pets\",\n"
			+ "        \"protocol\": \"HTTP/1.1\",\n"
			+ "        \"requestId\": \"BaYAChmeiGYEJyQ=\",\n"
			+ "        \"requestTime\": \"07/Mar/2023:13:19:59 +0000\",\n"
			+ "        \"requestTimeEpoch\": 1678195199907,\n"
			+ "        \"resourceId\": \"$default\",\n"
			+ "        \"resourcePath\": \"$default\",\n"
			+ "        \"stage\": \"$default\"\n"
			+ "    },\n"
			+ "    \"pathParameters\": null,\n"
			+ "    \"stageVariables\": null,\n"
			+ "    \"body\": null,\n"
			+ "    \"isBase64Encoded\": false\n"
			+ "}";


	@Test
	public void validateHttpServletRequestConstruction() throws Exception {
		System.setProperty("MAIN_CLASS", PetStoreSpringAppConfig.class.getName());
		WebProxyInvoker invoker = new WebProxyInvoker();
		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		Method prepareRequest  = ReflectionUtils.findMethod(WebProxyInvoker.class, "prepareRequest", InputStream.class);
		prepareRequest.setAccessible(true);
		ProxyHttpServletRequest request = (ProxyHttpServletRequest) prepareRequest.invoke(invoker, targetStream);
		assertThat(request.getContentType()).isEqualTo("application/json");
		assertThat(request.getParameterValues("foo").length).isEqualTo(2);
		assertThat(request.getParameterValues("foo")[0]).isEqualTo("bar");
	}

	@Test
	public void testApiGatewayProxy() throws Exception {
		System.setProperty("MAIN_CLASS", PetStoreSpringAppConfig.class.getName());
		WebProxyInvoker invoker = new WebProxyInvoker();

		InputStream targetStream = new ByteArrayInputStream(this.apiGatewayEvent.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output);

		ObjectMapper mapper = new ObjectMapper();
		System.out.println("RESULT: =======> " + new String(output.toByteArray()));
		Map result = mapper.readValue(output.toByteArray(), Map.class);
		System.out.println(result);
	}
}
