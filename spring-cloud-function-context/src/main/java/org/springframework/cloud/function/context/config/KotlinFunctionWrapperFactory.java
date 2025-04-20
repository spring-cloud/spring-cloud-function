/*
 * Copyright 2012-2021 the original author or authors.
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

package org.springframework.cloud.function.context.config;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.wrapper.KotlinConsumerSuspendWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinConsumerWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinFunctionSuspendWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinFunctionWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinSupplierSuspendWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinSupplierWrapper;

public final class KotlinFunctionWrapperFactory {
	private final Object kotlinLambdaTarget;

	private ConfigurableListableBeanFactory beanFactory;

	public KotlinFunctionWrapperFactory(Object kotlinLambdaTarget, ConfigurableListableBeanFactory beanFactory) {
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.beanFactory = beanFactory;
	}

	public FunctionRegistration getFunctionRegistration(String functionName) {
		String name = functionName.endsWith(FunctionRegistration.REGISTRATION_NAME_SUFFIX)
			? functionName.replace(FunctionRegistration.REGISTRATION_NAME_SUFFIX, "")
			: functionName;
		Type functionType = FunctionContextUtils.findType(name, beanFactory);

		Type[] types = ((ParameterizedType) functionType).getActualTypeArguments();

		if (KotlinSupplierWrapper.isValidSupplier(functionType, types)) {
			return KotlinSupplierWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		else if (KotlinSupplierSuspendWrapper.isValidSuspendSupplier(functionType, types)) {
			return KotlinSupplierSuspendWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		else if (KotlinConsumerWrapper.isValidConsumer(functionType, types)) {
			return KotlinConsumerWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		else if (KotlinConsumerSuspendWrapper.isValidSuspendConsumer(functionType, types)) {
			return KotlinConsumerSuspendWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		else if (KotlinFunctionWrapper.isValidFunction(functionType, types)) {
			return KotlinFunctionWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		else if (KotlinFunctionSuspendWrapper.isValidSuspendFunction(functionType, types)) {
			return KotlinFunctionSuspendWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		throw new UnsupportedOperationException("Multi argument Kotlin functions are not currently supported");
	}

}
