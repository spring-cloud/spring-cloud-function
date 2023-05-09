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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.serverless.web.ProxyHttpServletRequest;
import org.springframework.cloud.function.serverless.web.ProxyHttpServletResponse;
import org.springframework.cloud.function.serverless.web.ProxyMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

/**
 *
 * AWS Lambda specific handler that will proxy API Gateway request to Spring Web-app
 * This class represents AWS Lambda fronted by API Gateway  and is identified as 'handler' during the deployment.
 *
 * @author Oleg Zhurakousky
 *
 */
public class WebProxyInvoker {

	private static Log logger = LogFactory.getLog(WebProxyInvoker.class);

	private final ProxyMvc mvc;


	ObjectMapper mapper = new ObjectMapper();

	public WebProxyInvoker() throws ServletException {
		Class<?> startClass = FunctionClassUtils.getStartClass();
		this.mvc = ProxyMvc.INSTANCE(startClass);
	}

	/*
	 * TODO
	 * - Security context propagation from AWS API Gateway (easy)
	 * - Error handling
	 */
	@SuppressWarnings("unchecked")
	private HttpServletRequest prepareRequest(InputStream input) throws IOException {
		Map<String, Object> request = mapper.readValue(input, Map.class);
		if (logger.isInfoEnabled()) {
			logger.info("Request: " + request);
		}
		String httpMethod = (String) request.get("httpMethod");
		String path = (String) request.get("path");
		if (logger.isDebugEnabled()) {
			logger.debug("httpMethod: " + httpMethod);
			logger.debug("path: " + path);
		}
		ProxyHttpServletRequest httpRequest = new ProxyHttpServletRequest(null, httpMethod, path);

		// CONTENT
		if (StringUtils.hasText((String) request.get("body"))) {
			httpRequest.setContent(((String) request.get("body")).getBytes());
		}

		// REQUEST PARAM
		if (request.get("multiValueQueryStringParameters") != null) {
			Map<String, List<String>> parameters = (Map<String, List<String>>) request.get("multiValueQueryStringParameters");
			for (Entry<String, List<String>> parameter : parameters.entrySet()) {
				httpRequest.setParameter(parameter.getKey(), parameter.getValue().toArray(new String[] {}));
			}
		}

		// HEADERS
		Map<String, List<String>> headers = (Map<String, List<String>>) request.get("multiValueHeaders");
		HttpHeaders httpHeaders = new HttpHeaders();
		for (Entry<String, List<String>> entry : headers.entrySet()) {
			// TODO may need to do some header formatting
			httpHeaders.addAll(entry.getKey(), entry.getValue());
		}
		httpRequest.setHeaders(httpHeaders);

		return httpRequest;
	}


	public void handleRequest(InputStream input, OutputStream output) throws IOException {
		HttpServletRequest httpRequest = this.prepareRequest(input);

		ProxyHttpServletResponse httpResponse = new ProxyHttpServletResponse();
		try {
			this.mvc.service(httpRequest, httpResponse);
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new IllegalStateException(e);
		}

		String responseString = httpResponse.getContentAsString();
		if (StringUtils.hasText(responseString)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Response: " + responseString);
			}
			Map<String, Object> apiGatewayResponseStructure = new HashMap<String, Object>();
			apiGatewayResponseStructure.put("isBase64Encoded", false);
			apiGatewayResponseStructure.put("statusCode", HttpStatus.OK.value());
			apiGatewayResponseStructure.put("body", responseString);

			Map<String, List<String>> multiValueHeaders = new HashMap<>();
			Map<String, String> headers = new HashMap<>();
			for (String headerName : httpResponse.getHeaderNames()) {
				multiValueHeaders.put(headerName, httpResponse.getHeaders(headerName));
				headers.put(headerName, httpResponse.getHeaders(headerName).toString());
			}
			apiGatewayResponseStructure.put("multiValueHeaders", multiValueHeaders);
			apiGatewayResponseStructure.put("headers", headers);

			byte[] apiGatewayResponseBytes = mapper.writeValueAsBytes(apiGatewayResponseStructure);
			StreamUtils.copy(apiGatewayResponseBytes, output);
		}
	}
}
