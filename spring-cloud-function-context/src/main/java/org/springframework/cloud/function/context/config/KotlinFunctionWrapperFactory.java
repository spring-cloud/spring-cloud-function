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
import org.springframework.cloud.function.context.wrapper.KotlinConsumerFlowWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinConsumerPlainWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinConsumerSuspendWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinFunctionFlowToFlowWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinFunctionFlowToPlainWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinFunctionObjectToObjectWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinFunctionPlainToFlowWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinFunctionSuspendFlowToFlowWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinFunctionSuspendFlowToPlainWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinFunctionSuspendObjectToObjectWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinFunctionSuspendPlainToFlowWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinFunctionWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinSupplierFlowWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinSupplierPlainWrapper;
import org.springframework.cloud.function.context.wrapper.KotlinSupplierSuspendWrapper;


public final class KotlinFunctionWrapperFactory {

	private final Object kotlinLambdaTarget;
	private final ConfigurableListableBeanFactory beanFactory;

	public KotlinFunctionWrapperFactory(Object kotlinLambdaTarget, ConfigurableListableBeanFactory beanFactory) {
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.beanFactory = beanFactory;
	}

	public FunctionRegistration<KotlinFunctionWrapper> getFunctionRegistration(String functionName) {
		String name = functionName.endsWith(FunctionRegistration.REGISTRATION_NAME_SUFFIX)
			? functionName.replace(FunctionRegistration.REGISTRATION_NAME_SUFFIX, "")
			: functionName;
		Type functionType = FunctionContextUtils.findType(name, beanFactory);
		Type[] types = ((ParameterizedType) functionType).getActualTypeArguments();
		KotlinFunctionWrapper wrapper = null;
		if (KotlinConsumerFlowWrapper.isValid(functionType, types)) {
			wrapper = KotlinConsumerFlowWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		else if (KotlinFunctionFlowToFlowWrapper.isValid(functionType, types)) {
			wrapper = KotlinFunctionFlowToFlowWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		else if (KotlinFunctionSuspendFlowToFlowWrapper.isValid(functionType, types)) {
			wrapper = KotlinFunctionSuspendFlowToFlowWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		else if (KotlinFunctionFlowToPlainWrapper.isValid(functionType, types)) {
			wrapper = KotlinFunctionFlowToPlainWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		else if (KotlinFunctionSuspendFlowToPlainWrapper.isValid(functionType, types)) {
			wrapper = KotlinFunctionSuspendFlowToPlainWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		else if (KotlinFunctionPlainToFlowWrapper.isValid(functionType, types)) {
			wrapper = KotlinFunctionPlainToFlowWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		else if (KotlinFunctionSuspendPlainToFlowWrapper.isValid(functionType, types)) {
			wrapper = KotlinFunctionSuspendPlainToFlowWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		else if (KotlinSupplierFlowWrapper.isValid(functionType, types)) {
			wrapper = KotlinSupplierFlowWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		else if (KotlinSupplierPlainWrapper.isValid(functionType, types)) {
			wrapper = KotlinSupplierPlainWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		else if (KotlinSupplierSuspendWrapper.isValid(functionType, types)) {
			wrapper = KotlinSupplierSuspendWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		else if (KotlinConsumerPlainWrapper.isValid(functionType, types)) {
			wrapper = KotlinConsumerPlainWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		else if (KotlinConsumerSuspendWrapper.isValid(functionType, types)) {
			wrapper = KotlinConsumerSuspendWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		else if (KotlinFunctionObjectToObjectWrapper.isValid(functionType, types)) {
			wrapper = KotlinFunctionObjectToObjectWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, functionType, types);
		}
		else if (KotlinFunctionSuspendObjectToObjectWrapper.isValid(functionType, types)) {
			wrapper = KotlinFunctionSuspendObjectToObjectWrapper.asRegistrationFunction(functionName, kotlinLambdaTarget, types);
		}
		if (wrapper == null) {
			throw new IllegalStateException("Unable to create function wrapper for " + functionName);
		}

		FunctionRegistration<KotlinFunctionWrapper> registration = new FunctionRegistration<>(wrapper, wrapper.getName());
		registration.type(wrapper.getResolvableType().getType());
		return registration;
	}

}
