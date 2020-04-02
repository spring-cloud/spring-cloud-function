/*
 * Copyright 2020-2020 the original author or authors.
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

package org.springframework.cloud.function.adapter.gcloud;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;
import java.util.function.Function;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

import org.springframework.cloud.function.context.AbstractSpringFunctionAdapterInitializer;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;
import org.springframework.util.MimeTypeUtils;

/**
 * Implementation of {@link HttpFunction} for Google Cloud Function (GCF).
 * This is the Spring Cloud Function adapter for GCF HTTP function.
 *
 * @author Dmitry Solomakha
 * @author Mike Eltsufin
 * @author Oleg Zhurakousky
 *
 * @since 3.0.4
 */
public class FunctionInvoker
	extends AbstractSpringFunctionAdapterInitializer<HttpRequest> implements HttpFunction {

	public FunctionInvoker() {
		super();
		init();
	}

	public FunctionInvoker(Class<?> configurationClass) {
		super(configurationClass);
		init();
	}

	private void init() {
		System.setProperty("spring.http.converters.preferred-json-mapper", "gson");
		Thread.currentThread() //TODO investigate if it is necessary
				.setContextClassLoader(FunctionInvoker.class.getClassLoader());
		initialize(null);
	}

	/**
	 * The implementation of a GCF {@link HttpFunction} that will be used as the entry point from GCF.
	 */
	@Override
	public void service(HttpRequest httpRequest, HttpResponse httpResponse) throws Exception {
		try {
			String functionName = System.getenv().containsKey("spring.cloud.function.definition")
					? System.getenv("spring.cloud.function.definition") : "";

			Function<Message<BufferedReader>, Message<byte[]>> function =
					this.catalog.lookup(functionName, MimeTypeUtils.APPLICATION_JSON.toString());
			Assert.notNull(function, "'function' with name '" + functionName + "' must not be null");

			Message<BufferedReader> message = getInputType() == Void.class
					? null : MessageBuilder.withPayload(httpRequest.getReader())
								.copyHeaders(httpRequest.getHeaders())
								.build();
			Message<byte[]> result = function.apply(message);

			if (result != null) {
				httpResponse.getWriter().write(new String(result.getPayload(), StandardCharsets.UTF_8));
				for (Entry<String, Object> header : result.getHeaders().entrySet()) {
					httpResponse.appendHeader(header.getKey(), header.getValue().toString());
				}
			}
		}
		finally {
			httpResponse.getWriter().close();
		}
	}
}
