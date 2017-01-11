/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.web.flux;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.AsyncHandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 */
@Configuration
@ConditionalOnWebApplication
@ConditionalOnClass({ Flux.class, AsyncHandlerMethodReturnValueHandler.class })
public class ReactorAutoConfiguration extends WebMvcConfigurerAdapter {

	@Autowired
	private FluxReturnValueHandler returnValueHandler;

	@Bean
	public FluxReturnValueHandler fluxReturnValueHandler(
			HttpMessageConverters converters) {
		return new FluxReturnValueHandler(converters.getConverters());
	}

	@Configuration
	protected static class MessageConverters {
		@Bean
		public HttpMessageConverters httpMessageConverters() {
			return new HttpMessageConverters(new FluxHttpMessageConverter());
		}
	}

	@Override
	public void addReturnValueHandlers(
			List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		returnValueHandlers.add(returnValueHandler);
	}

}
