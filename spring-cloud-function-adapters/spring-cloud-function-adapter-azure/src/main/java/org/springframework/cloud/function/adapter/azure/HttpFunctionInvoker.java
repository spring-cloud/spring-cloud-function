/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.function.adapter.azure;

import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;


/**
 * Implementation of HTTP Request Handler for Azure which supports
 * HttpRequestMessage and HttpResponseMessage the types required by
 * Azure Functions for HTTP-triggered functions.
 *
 * @param <I> input type
 * @author Oleg Zhurakousky
 *
 * @since 3.2
 */
public class HttpFunctionInvoker<I> extends
		FunctionInvoker<HttpRequestMessage<I>, HttpResponseMessage> {

	public HttpFunctionInvoker(Class<?> configurationClass) {
		super(configurationClass);
	}

	public HttpFunctionInvoker() {
		super();
	}
}
