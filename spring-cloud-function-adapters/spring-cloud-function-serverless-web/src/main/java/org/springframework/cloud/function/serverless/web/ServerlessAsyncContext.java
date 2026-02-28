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
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.util.Assert;
import org.springframework.web.util.WebUtils;

/**
 * Implementation of Async context for {@link ServerlessMVC}.
 *
 * @author Oleg Zhurakousky
 * @author Omer Celik
 */
public class ServerlessAsyncContext implements AsyncContext {
	private final HttpServletRequest request;

	@Nullable
	private final HttpServletResponse response;

	private final List<AsyncListener> listeners = new ArrayList<>();

	@Nullable
	private String dispatchedPath;

	private long timeout = 10 * 1000L;

	private final List<Runnable> dispatchHandlers = new ArrayList<>();

	private final ReentrantLock globalLock = new ReentrantLock();


	public ServerlessAsyncContext(ServletRequest request, @Nullable ServletResponse response) {
		this.request = (HttpServletRequest) request;
		this.response = (HttpServletResponse) response;
	}


	public void addDispatchHandler(Runnable handler) {
		Assert.notNull(handler, "Dispatch handler must not be null");
		try {
			this.globalLock.lock();
			if (this.dispatchedPath == null) {
				this.dispatchHandlers.add(handler);
			}
			else {
				handler.run();
			}
		}
		finally {
			this.globalLock.unlock();
		}
	}

	@Override
	public ServletRequest getRequest() {
		return this.request;
	}

	@Override
	@Nullable
	public ServletResponse getResponse() {
		return this.response;
	}

	@Override
	public boolean hasOriginalRequestAndResponse() {
		return (this.request instanceof ServerlessHttpServletRequest && this.response instanceof ServerlessHttpServletResponse);
	}

	@Override
	public void dispatch() {
		dispatch(this.request.getRequestURI());
	}

	@Override
	public void dispatch(String path) {
		dispatch(null, path);
	}

	@Override
	public void dispatch(@Nullable ServletContext context, String path) {
		try {
			this.globalLock.lock();
			this.dispatchedPath = path;
			this.dispatchHandlers.forEach(Runnable::run);
		}
		finally {
			this.globalLock.unlock();
		}
	}

	@Nullable
	public String getDispatchedPath() {
		return this.dispatchedPath;
	}

	@Override
	public void complete() {
		ServerlessHttpServletRequest mockRequest = WebUtils.getNativeRequest(this.request, ServerlessHttpServletRequest.class);
		if (mockRequest != null) {
			mockRequest.setAsyncStarted(false);
		}
		for (AsyncListener listener : this.listeners) {
			try {
				listener.onComplete(new AsyncEvent(this, this.request, this.response));
			}
			catch (IOException ex) {
				throw new IllegalStateException("AsyncListener failure", ex);
			}
		}
	}

	@Override
	public void start(Runnable runnable) {
		runnable.run();
	}

	@Override
	public void addListener(AsyncListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void addListener(AsyncListener listener, ServletRequest request, ServletResponse response) {
		this.listeners.add(listener);
	}

	public List<AsyncListener> getListeners() {
		return this.listeners;
	}

	@Override
	public <T extends AsyncListener> T createListener(Class<T> clazz) throws ServletException {
		return BeanUtils.instantiateClass(clazz);
	}

	/**
	 * By default this is set to 10000 (10 seconds) even though the Servlet API
	 * specifies a default async request timeout of 30 seconds. Keep in mind the
	 * timeout could further be impacted by global configuration through the MVC
	 * Java config or the XML namespace, as well as be overridden per request on
	 * {@link org.springframework.web.context.request.async.DeferredResult DeferredResult}
	 * or on
	 * {@link org.springframework.web.servlet.mvc.method.annotation.SseEmitter SseEmitter}.
	 * @param timeout the timeout value to use.
	 * @see AsyncContext#setTimeout(long)
	 */
	@Override
	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	@Override
	public long getTimeout() {
		return this.timeout;
	}
}
