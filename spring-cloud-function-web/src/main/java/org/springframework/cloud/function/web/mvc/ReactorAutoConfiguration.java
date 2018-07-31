/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.function.web.mvc;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.web.RequestProcessor;
import org.springframework.cloud.function.web.StringConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.web.method.support.AsyncHandlerMethodReturnValueHandler;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 */
@Configuration
@ConditionalOnWebApplication(type=Type.SERVLET)
@ConditionalOnClass({ Flux.class, AsyncHandlerMethodReturnValueHandler.class })
@Import({ FunctionController.class, RequestProcessor.class })
@AutoConfigureAfter({ JacksonAutoConfiguration.class, GsonAutoConfiguration.class })
public class ReactorAutoConfiguration {

	@Bean
	public FunctionHandlerMapping functionHandlerMapping(FunctionCatalog catalog,
			FunctionController controller) {
		return new FunctionHandlerMapping(catalog, controller);
	}

	@Bean
	@ConditionalOnMissingBean
	public StringConverter functionStringConverter(FunctionInspector inspector,
			ConfigurableListableBeanFactory beanFactory) {
		return new BasicStringConverter(inspector, beanFactory);
	}

	private static class BasicStringConverter implements StringConverter {

		private ConversionService conversionService;
		private ConfigurableListableBeanFactory registry;
		private FunctionInspector inspector;

		public BasicStringConverter(FunctionInspector inspector,
				ConfigurableListableBeanFactory registry) {
			this.inspector = inspector;
			this.registry = registry;
		}

		@Override
		public Object convert(Object function, String value) {
			if (conversionService == null && registry != null) {
				ConversionService conversionService = this.registry
						.getConversionService();
				this.conversionService = conversionService != null ? conversionService
						: new DefaultConversionService();
			}
			Class<?> type = inspector.getInputType(function);
			return conversionService.canConvert(String.class, type)
					? conversionService.convert(value, type)
					: value;
		}

	}
}
