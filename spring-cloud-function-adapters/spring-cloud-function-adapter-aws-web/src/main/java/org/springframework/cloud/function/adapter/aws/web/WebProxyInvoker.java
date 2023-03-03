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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.Filter;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ProxyHttpServletRequest;
import org.springframework.web.client.ProxyHttpServletResponse;
import org.springframework.web.client.ProxyMvc;
import org.springframework.web.client.ProxyServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

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

	private final ServletContext servletContext;

	ObjectMapper mapper = new ObjectMapper();

	public WebProxyInvoker() throws ServletException {
		Class<?> startClass = FunctionClassUtils.getStartClass();
		AnnotationConfigWebApplicationContext applpicationContext = new AnnotationConfigWebApplicationContext();
		applpicationContext.register(startClass);

		this.servletContext = new ProxyServletContext();
		ServletConfig servletConfig = new ProxyServletConfig(this.servletContext);

		DispatcherServlet servlet = new DispatcherServlet(applpicationContext);
		servlet.init(servletConfig);
		this.mvc = new ProxyMvc(servlet, applpicationContext);
	}

	/*
	 * TODO
	 * - Security context propagation from AWS API Gateway (easy)
	 * - Error handling
	 */
	@SuppressWarnings("unchecked")
	private HttpServletRequest prepareRequest(InputStream input) throws IOException {

		Map<String, Object> request = mapper.readValue(input, Map.class);
		if (logger.isDebugEnabled()) {
			logger.debug("Request: " + request);
		}
		String httpMethod = (String) request.get("httpMethod");
		String path = (String) request.get("path");
		if (logger.isDebugEnabled()) {
			logger.debug("httpMethod: " + httpMethod);
			logger.debug("path: " + path);
		}
		ProxyHttpServletRequest httpRequest = new ProxyHttpServletRequest(servletContext, httpMethod, path);
		if (StringUtils.hasText((String) request.get("body"))) {
			httpRequest.setContent(((String) request.get("body")).getBytes());
		}
		if (request.get("queryStringParameters") != null) {
			httpRequest.setParameters((Map<String, ?>) request.get("queryStringParameters"));
		}

		Map<String, Object> headers = (Map<String, Object>) request.get("headers");
		headers.putAll((Map<String, Object>) request.get("multiValueHeaders"));
		for (Entry<String, Object> entry : headers.entrySet()) {
			httpRequest.addHeader(entry.getKey(), entry.getValue());
		}
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
			apiGatewayResponseStructure.put("statusCode", 200);
			apiGatewayResponseStructure.put("body", responseString);

			Map<String, List<String>> multiValueHeaders = new HashMap<>();
			for (String headerName : httpResponse.getHeaderNames()) {
				multiValueHeaders.put(headerName, httpResponse.getHeaders(headerName));
			}
			// TODO investigate why AWS doesn't like List as value
//			apiGatewayResponseStructure.put("headers", multiValueHeaders);

			byte[] apiGatewayResponseBytes = mapper.writeValueAsBytes(apiGatewayResponseStructure);
			StreamUtils.copy(apiGatewayResponseBytes, output);
		}
	}

	private static class ProxyServletConfig implements ServletConfig {

		private final ServletContext servletContext;

		ProxyServletConfig(ServletContext servletContext) {
			this.servletContext = servletContext;
		}

		@Override
		public String getServletName() {
			return "serverless-proxy";
		}

		@Override
		public ServletContext getServletContext() {
			return this.servletContext;
		}

		@Override
		public Enumeration<String> getInitParameterNames() {
			return Collections.enumeration(new ArrayList<String>());
		}

		@Override
		public String getInitParameter(String name) {
			return null;
		}
	}
}
