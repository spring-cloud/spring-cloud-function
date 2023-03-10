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

import java.util.Map.Entry;
import java.util.Optional;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

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

import org.springframework.cloud.function.serverless.web.FunctionClassUtils;
import org.springframework.cloud.function.serverless.web.ProxyHttpServletRequest;
import org.springframework.cloud.function.serverless.web.ProxyHttpServletResponse;
import org.springframework.cloud.function.serverless.web.ProxyMvc;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 *
 * @author Christian Tzolov
 * @author Oleg Zhurakousky
 *
 */
public class AzureWebProxyInvoker implements FunctionInstanceInjector {

	private static Log logger = LogFactory.getLog(AzureWebProxyInvoker.class);

	private static final String AZURE_WEB_ADAPTER_NAME = "AzureWebAdapter";
	private static final String AZURE_WEB_ADAPTER_ROUTE = AZURE_WEB_ADAPTER_NAME
			+ "/{e?}/{e2?}/{e3?}/{e4?}/{e5?}/{e6?}/{e7?}/{e8?}/{e9?}/{e10?}/{e11?}/{e12?}/{e13?}/{e14?}/{e15?}";

	private ProxyMvc mvc;

	private ServletContext servletContext;

	@Override
	public <T> T getInstance(Class<T> functionClass) throws Exception {
	//	System.setProperty("MAIN_CLASS", "oz.spring.petstore.PetStoreSpringAppConfig");
		this.initialize();
		return (T) this;
	}

	/**
	 * Because the getInstance is called by Azure Java Function on every function request we need to cache the Spring
	 * context initialization on the first function call.
	 * @throws ServletException error.
	 */
	private void initialize() throws ServletException {
		synchronized (AzureWebProxyInvoker.class.getName()) {
			if (mvc == null) {
				Class<?> startClass = FunctionClassUtils.getStartClass();
				this.mvc = ProxyMvc.INSTANCE(startClass);
			}
		}
	}

	private HttpServletRequest prepareRequest(HttpRequestMessage<Optional<String>> request) {

		int pathOffset = request.getUri().getPath().indexOf(AZURE_WEB_ADAPTER_NAME) + AZURE_WEB_ADAPTER_NAME.length();

		String path = request.getUri().getPath().substring(pathOffset);

		ProxyHttpServletRequest httpRequest = new ProxyHttpServletRequest(servletContext,
				request.getHttpMethod().toString(), path);

		request.getBody().ifPresent(body -> {
			httpRequest.setContent(body.getBytes());
		});

		if (!CollectionUtils.isEmpty(request.getQueryParameters())) {
			httpRequest.setParameters(request.getQueryParameters());
		}

		if (!CollectionUtils.isEmpty(request.getHeaders())) {
			for (Entry<String, String> entry : request.getHeaders().entrySet()) {
				httpRequest.addHeader(entry.getKey(), entry.getValue());
			}
		}

		return httpRequest;
	}

	@FunctionName(AZURE_WEB_ADAPTER_NAME)
	public HttpResponseMessage execute(
			@HttpTrigger(name = "req", methods = { HttpMethod.GET,
					HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS, route = AZURE_WEB_ADAPTER_ROUTE) HttpRequestMessage<Optional<String>> request,
			ExecutionContext context) {

		context.getLogger().info("Request body is: " + request.getBody().orElse("[empty]"));

		HttpServletRequest httpRequest = this.prepareRequest(request);

		ProxyHttpServletResponse httpResponse = new ProxyHttpServletResponse();
		try {
			this.mvc.service(httpRequest, httpResponse);

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
}
