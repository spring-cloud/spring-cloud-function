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

package org.springframework.cloud.function.adapter.azure.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map.Entry;
import java.util.Optional;

import javax.servlet.Filter;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpResponseMessage.Builder;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.spi.inject.FunctionInstanceInjector;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ProxyHttpServletRequest;
import org.springframework.web.client.ProxyHttpServletResponse;
import org.springframework.web.client.ProxyMvc;
import org.springframework.web.client.ProxyServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 *
 * @author Christian Tzolov
 * @author Oleg Zhurakousky
 *
 */
public class WebProxyInvoker implements FunctionInstanceInjector {

	private static Log logger = LogFactory.getLog(WebProxyInvoker.class);

	private ProxyMvc mvc;

	private ServletContext servletContext;

	ObjectMapper mapper = new ObjectMapper();

	@Override
	public <T> T getInstance(Class<T> functionClass) throws Exception {
		System.setProperty("MAIN_CLASS", "oz.spring.petstore.PetStoreSpringAppConfig");
		// TODO: Cache the initialization as the getInstance is called before each function invokatoin
		this.initialize();
		return (T) this;
	}

	public void initialize() throws ServletException {
		Class<?> startClass = FunctionClassUtils.getStartClass();
		AnnotationConfigWebApplicationContext applicationContext = new AnnotationConfigWebApplicationContext();
		applicationContext.register(startClass);

		this.servletContext = new ProxyServletContext();
		ServletConfig servletConfig = new ProxyServletConfig(this.servletContext);

		DispatcherServlet servlet = new DispatcherServlet(applicationContext);
		servlet.init(servletConfig);
		this.mvc = new ProxyMvc(servlet,
				applicationContext.getBeansOfType(Filter.class).values().toArray(new Filter[0]));
	}

	private HttpServletRequest prepareRequest(HttpRequestMessage<Optional<String>> request) {

		String path = request.getQueryParameters().get("path");

		if (!StringUtils.hasText(path)) {
			throw new IllegalStateException("Missing path parameter");
		}
		ProxyHttpServletRequest httpRequest = new ProxyHttpServletRequest(servletContext,
				request.getHttpMethod().toString(), path);

		if (request.getBody().isPresent()) {
			httpRequest.setContent(request.getBody().get().getBytes());
		}

		if (!CollectionUtils.isEmpty(request.getQueryParameters())) {
			httpRequest.setParameters(request.getQueryParameters());
		}

		for (Entry<String, String> entry : request.getHeaders().entrySet()) {
			httpRequest.addHeader(entry.getKey(), entry.getValue());
		}

		return httpRequest;
	}

	@FunctionName("AzureWebAdapter")
	public HttpResponseMessage execute(
			@HttpTrigger(name = "req", methods = { HttpMethod.GET,
					HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
			ExecutionContext context) {

		context.getLogger().info("Request body is: " + request.getBody().orElse("[empty]"));

		HttpServletRequest httpRequest = this.prepareRequest(request);

		ProxyHttpServletResponse httpResponse = new ProxyHttpServletResponse();
		try {
			this.mvc.perform(httpRequest, httpResponse);

			Builder responseBuilder = request.createResponseBuilder(HttpStatus.OK);
			for (String headerName : httpResponse.getHeaderNames()) {
				responseBuilder.header(headerName, httpResponse.getHeader(headerName));
			}

			String responseString = httpResponse.getContentAsString();
			if (StringUtils.hasText(responseString)) {
				if (logger.isDebugEnabled()) {
					logger.debug("Response: " + responseString);
				}
				responseBuilder.body(responseString);
			} // TODO: what to do with bodyless response?

			return responseBuilder.build();
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new IllegalStateException(e);
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
