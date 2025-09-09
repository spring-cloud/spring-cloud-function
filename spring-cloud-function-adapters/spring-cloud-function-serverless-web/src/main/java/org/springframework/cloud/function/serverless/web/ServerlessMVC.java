/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.cloud.function.serverless.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;


import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Represents the main entry point into interaction with web application over light-weight proxy.
 * After creating an instance via {@link #INSTANCE(Class...)} operation which will initialize the provided component
 * classes of your web application (effectively starting your web application less web server),
 * you use {@link #service(HttpServletRequest, HttpServletResponse)} operation to send request and receive a response.
 *
 * @author Oleg Zhurakousky
 *
 */
public final class ServerlessMVC {

	/**
	 * Name of the property to specify application context initialization timeout. Default is 20 sec.
	 */
	public static String INIT_TIMEOUT = "contextInitTimeout";

	private static Log LOG = LogFactory.getLog(ServerlessMVC.class);

	private volatile DispatcherServlet dispatcher;

	private volatile ServletWebServerApplicationContext applicationContext;

	private final CountDownLatch contextStartupLatch = new CountDownLatch(1);

	private final long initializationTimeout;

	public static ServerlessMVC INSTANCE(Class<?>... componentClasses) {
		ServerlessMVC mvc = new ServerlessMVC();
		mvc.initializeContextAsync(componentClasses);
		return mvc;
	}

	public static ServerlessMVC INSTANCE(ServletWebServerApplicationContext applicationContext) {
		ServerlessMVC mvc = new ServerlessMVC();
		mvc.applicationContext = applicationContext;
		mvc.dispatcher = mvc.applicationContext.getBean(DispatcherServlet.class);
		mvc.contextStartupLatch.countDown();
		return mvc;
	}

	private ServerlessMVC() {
		String timeoutValue = System.getenv(INIT_TIMEOUT);
		if (!StringUtils.hasText(timeoutValue)) {
			timeoutValue = System.getProperty(INIT_TIMEOUT);
		}
		this.initializationTimeout = StringUtils.hasText(timeoutValue) ? Long.valueOf(timeoutValue) : 20000;
	}

	private void initializeContextAsync(Class<?>... componentClasses) {
		new Thread(() -> {
			try {
				LOG.info("Starting application with the following configuration classes:");
				Stream.of(componentClasses).forEach(clazz -> LOG.info(clazz.getSimpleName()));
				initContext(componentClasses);
			}
			catch (Exception e) {
				throw new IllegalStateException(e);
			}
			finally {
				contextStartupLatch.countDown();
				LOG.info("Application is started successfully.");
			}
		}).start();
	}

	private void initContext(Class<?>... componentClasses) {
		this.applicationContext = (ServletWebServerApplicationContext) SpringApplication.run(componentClasses, new String[] {});
		if (this.applicationContext.containsBean(DispatcherServletAutoConfiguration.DEFAULT_DISPATCHER_SERVLET_BEAN_NAME)) {
			this.dispatcher = this.applicationContext.getBean(DispatcherServlet.class);
		}
	}

	public ConfigurableWebApplicationContext getApplicationContext() {
		this.waitForContext();
		return this.applicationContext;
	}

	public ServletContext getServletContext() {
		this.waitForContext();
		return this.dispatcher.getServletContext();
	}

	public void stop() {
		this.waitForContext();
		this.applicationContext.stop();
	}

	/**
	 * Perform a request and return a type that allows chaining further actions,
	 * such as asserting expectations, on the result.
	 *
	 * @param requestBuilder used to prepare the request to execute; see static
	 *                       factory methods in
	 *                       {@link org.springframework.test.web.servlet.request.MockMvcRequestBuilders}
	 * @return an instance of {@link ResultActions} (never {@code null})
	 * @see org.springframework.test.web.servlet.request.MockMvcRequestBuilders
	 * @see org.springframework.test.web.servlet.result.MockMvcResultMatchers
	 */
	public void service(HttpServletRequest request, HttpServletResponse response) throws Exception {
		Assert.state(this.waitForContext(), "Failed to initialize Application within the specified time of " + this.initializationTimeout + " milliseconds. "
				+ "If you need to increase it, please set " + INIT_TIMEOUT + " environment variable");
		this.service(request, response, (CountDownLatch) null);
	}

	public void service(HttpServletRequest request, HttpServletResponse response, CountDownLatch latch) throws Exception {
		ProxyFilterChain filterChain = new ProxyFilterChain(this.dispatcher);
		filterChain.doFilter(request, response);

		AsyncContext asyncContext = request.getAsyncContext();
		if (asyncContext != null) {
			filterChain = new ProxyFilterChain(this.dispatcher);
			if (asyncContext instanceof ServerlessAsyncContext proxyAsyncContext) {
				proxyAsyncContext.addDispatchHandler(() -> {
					try {
						new ProxyFilterChain(this.dispatcher).doFilter(request, response);
						asyncContext.complete();
					}
					catch (Exception e) {
						throw new IllegalStateException(e);
					}
				});
			}
		}

		if (latch != null) {
			latch.countDown();
		}
	}

	public boolean waitForContext() {
		try {
			return contextStartupLatch.await(initializationTimeout, TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return false;
	}

	private static class ProxyFilterChain implements FilterChain {

		@Nullable
		private ServletRequest request;

		@Nullable
		private ServletResponse response;

		private final List<Filter> filters;

		@Nullable
		private Iterator<Filter> iterator;


		/**
		 * Create a {@code FilterChain} with Filter's and a Servlet.
		 *
		 * @param servlet the {@link Servlet} to invoke in this {@link FilterChain}
		 * @since 4.0.x
		 */
		ProxyFilterChain(DispatcherServlet servlet) {
			List<Filter> filters = new ArrayList<>();
			servlet.getServletContext().getFilterRegistrations().values().forEach(fr -> filters.add(((ServerlessFilterRegistration) fr).getFilter()));
			Assert.notNull(filters, "filters cannot be null");
			Assert.noNullElements(filters, "filters cannot contain null values");
			this.filters = initFilterList(servlet, filters.toArray(new Filter[] {}));
		}

		private static List<Filter> initFilterList(Servlet servlet, Filter... filters) {
			Filter[] allFilters = ObjectUtils.addObjectToArray(filters, new ServletFilterProxy(servlet));
			return Arrays.asList(allFilters);
		}

		/**
		 * Return the request that {@link #doFilter} has been called with.
		 */
		@Nullable
		public ServletRequest getRequest() {
			return this.request;
		}

		/**
		 * Return the response that {@link #doFilter} has been called with.
		 */
		@Nullable
		public ServletResponse getResponse() {
			return this.response;
		}

		/**
		 * Invoke registered {@link Filter Filters} and/or {@link Servlet} also saving
		 * the request and response.
		 */
		@Override
		public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
			Assert.notNull(request, "Request must not be null");
			Assert.notNull(response, "Response must not be null");
			Assert.state(this.request == null, "This FilterChain has already been called!");

			if (this.iterator == null) {
				this.iterator = this.filters.iterator();
			}

			if (this.iterator.hasNext()) {
				Filter nextFilter = this.iterator.next();
				nextFilter.doFilter(request, response, this);
			}

			this.request = request;
			this.response = response;

			if (!response.isCommitted() && request.getDispatcherType() != DispatcherType.ASYNC) {
				response.flushBuffer();
			}
		}

		/**
		 * A filter that simply delegates to a Servlet.
		 */
		private static final class ServletFilterProxy implements Filter {

			private final Servlet delegateServlet;

			private ServletFilterProxy(Servlet servlet) {
				Assert.notNull(servlet, "servlet cannot be null");
				this.delegateServlet = servlet;
			}

			@Override
			public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
					throws IOException, ServletException {

				try {
					if (((HttpServletResponse) response).getStatus() != HttpStatus.OK.value() && request instanceof ServerlessHttpServletRequest) {
						((HttpServletRequest) request).setAttribute(RequestDispatcher.ERROR_STATUS_CODE, ((HttpServletResponse) response).getStatus());
						this.setErrorMessageAttribute((ServerlessHttpServletRequest) request, (ServerlessHttpServletResponse) response, null);
						((HttpServletRequest) request).setAttribute(RequestDispatcher.ERROR_REQUEST_URI, ((HttpServletRequest) request).getRequestURI());

						((ServerlessHttpServletRequest) request).setRequestURI("/error");
						this.delegateServlet.service(request, response);
					}
					else {
						this.delegateServlet.service(request, response);
					}
				}
				catch (Exception e) {
					if (request instanceof ServerlessHttpServletRequest) {
						((HttpServletRequest) request).setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.INTERNAL_SERVER_ERROR.value());
						this.setErrorMessageAttribute((HttpServletRequest) request, (HttpServletResponse) response, e);
						((HttpServletRequest) request).setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE, e);
						((HttpServletRequest) request).setAttribute(RequestDispatcher.ERROR_REQUEST_URI, ((HttpServletRequest) request).getRequestURI());
						((ServerlessHttpServletRequest) request).setRequestURI("/error");
					}

					LOG.error("Failed processing the request to: " + ((HttpServletRequest) request).getRequestURI(), e);

					this.delegateServlet.service(request, response);
				}
			}

			private void setErrorMessageAttribute(HttpServletRequest request, HttpServletResponse response, Exception exception) {
				if (exception != null && StringUtils.hasText(exception.getMessage())) {
					request.setAttribute(RequestDispatcher.ERROR_MESSAGE, exception.getMessage());
				}
				else if (response instanceof ServerlessHttpServletResponse proxyResponse && StringUtils.hasText(proxyResponse.getErrorMessage())) {
					request.setAttribute(RequestDispatcher.ERROR_MESSAGE, proxyResponse.getErrorMessage());
				}
				else {
					request.setAttribute(RequestDispatcher.ERROR_MESSAGE, HttpStatus.valueOf(response.getStatus()).getReasonPhrase());

				}
			}

			@Override
			public void init(FilterConfig filterConfig) throws ServletException {
			}

			@Override
			public void destroy() {
			}

			@Override
			public String toString() {
				return this.delegateServlet.toString();
			}
		}
	}

	public static class ProxyServletConfig implements ServletConfig {

		private final ServletContext servletContext;

		public ProxyServletConfig(ServletContext servletContext) {
			this.servletContext = servletContext;
		}

		@Override
		public String getServletName() {
			return DispatcherServletAutoConfiguration.DEFAULT_DISPATCHER_SERVLET_BEAN_NAME;
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
