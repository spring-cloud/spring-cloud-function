/*
 * Copyright 2012-2020 the original author or authors.
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

package org.springframework.cloud.function.web.flux;

import reactor.core.publisher.Flux;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.web.BasicStringConverter;
import org.springframework.cloud.function.web.StringConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.method.support.AsyncHandlerMethodReturnValueHandler;

/**
 * @author Dave Syer
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ Flux.class, AsyncHandlerMethodReturnValueHandler.class })
@ConditionalOnWebApplication(type = Type.REACTIVE)
@Import(FunctionController.class)
@AutoConfigureAfter({ JacksonAutoConfiguration.class, GsonAutoConfiguration.class })
public class ReactorAutoConfiguration {

	@Bean
	public FunctionHandlerMapping functionHandlerMapping(FunctionCatalog catalog, FunctionController controller, FunctionProperties functionProperties) {
		return new FunctionHandlerMapping(catalog, controller, functionProperties);
	}

	@Bean
	@ConditionalOnMissingBean
	public StringConverter functionStringConverter(ConfigurableListableBeanFactory beanFactory) {
		return new BasicStringConverter(beanFactory);
	}

}
