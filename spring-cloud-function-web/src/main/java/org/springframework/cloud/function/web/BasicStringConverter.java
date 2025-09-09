/*
 * Copyright 2012-present the original author or authors.
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

package org.springframework.cloud.function.web;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

/**
 * Simple implementation of a {@link StringConverter}.
 *
 * @author Dave Syer
 */
public class BasicStringConverter implements StringConverter {

	private ConversionService conversionService;

	private ConfigurableListableBeanFactory registry;

	public BasicStringConverter(ConfigurableListableBeanFactory registry) {
		this.registry = registry;
	}

	@Override
	public Object convert(Object function, String value) {
		if (this.conversionService == null && this.registry != null) {
			ConversionService conversionService = this.registry.getConversionService();
			this.conversionService = conversionService != null ? conversionService
					: new DefaultConversionService();
		}
		//Class<?> type = this.inspector.getInputType(function);
		Class<?> type = function == null ? Object.class : FunctionTypeUtils.getRawType(FunctionTypeUtils.getGenericType(((FunctionInvocationWrapper) function).getInputType()));
		return this.conversionService.canConvert(String.class, type)
				? this.conversionService.convert(value, type) : value;
	}

}
