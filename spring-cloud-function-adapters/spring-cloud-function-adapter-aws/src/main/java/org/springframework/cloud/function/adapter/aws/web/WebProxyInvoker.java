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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.cloud.function.utils.FunctionClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ProxyDispatcherServlet;
import org.springframework.web.client.ProxyHttpServletRequest;
import org.springframework.web.client.ProxyHttpServletResponse;
import org.springframework.web.client.ProxyMvc;
import org.springframework.web.client.ProxyServletConfig;
import org.springframework.web.client.ProxyServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;



public class WebProxyInvoker implements RequestStreamHandler {

	private final ProxyMvc mvc;

	public WebProxyInvoker() throws ServletException {
		Class<?> startClass = FunctionClassUtils.getStartClass();
		AnnotationConfigWebApplicationContext applpicationContext = new AnnotationConfigWebApplicationContext();
		applpicationContext.register(startClass);
		ServletContext servletContext = new ProxyServletContext();
		ServletConfig servletConfig = new ProxyServletConfig(servletContext);
		applpicationContext.setServletConfig(servletConfig);

		DispatcherServlet servlet = new DispatcherServlet(applpicationContext);
		servlet.init(servletConfig);
		this.mvc = new ProxyMvc(servlet, applpicationContext.getBeansOfType(Filter.class).values().toArray(new Filter[0]));
	}

	@Override
	public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
		ProxyServletContext servletContext = new ProxyServletContext();
		JsonMapper mapper = new JacksonMapper(new ObjectMapper());
		Map<String, Object> request = mapper.fromJson(StreamUtils.copyToByteArray(input), Map.class);
		System.out.println("!!!==> REQUEST: " + request);
		String httpMethod = (String) request.get("httpMethod");
		String path = (String) request.get("path");
		System.out.println("!!!==> httpMethod: " + httpMethod);
		System.out.println("!!!==> path: " + path);
		HttpServletRequest resquest = new ProxyHttpServletRequest(null, httpMethod, path);
		ProxyHttpServletResponse response = new ProxyHttpServletResponse();
		try {
			this.mvc.perform(resquest, response);
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new IllegalStateException(e);
		}
		byte[] responseBytes = response.getContentAsByteArray();
		if (!ObjectUtils.isEmpty(responseBytes)) {
			System.out.println("!!!==> responseBytes: " + response.getContentAsString());

			Map<String, Object> apiGatewayResponseStructure = new HashMap<String, Object>();
			apiGatewayResponseStructure.put("isBase64Encoded", false);
			apiGatewayResponseStructure.put("statusCode", 200);
			apiGatewayResponseStructure.put("body", response.getContentAsString());
			apiGatewayResponseStructure.put("headers", Collections.singletonMap("foo", "bar"));

			byte[] apiGatewayResponseBytes = mapper.toJson(apiGatewayResponseStructure);
			System.out.println("!!!==> apiGatewayResponseStructure: " + apiGatewayResponseStructure);
			StreamUtils.copy(apiGatewayResponseBytes, output);
			System.out.println("!!!==> COPIED RESPONSE");
		}
	}
}
