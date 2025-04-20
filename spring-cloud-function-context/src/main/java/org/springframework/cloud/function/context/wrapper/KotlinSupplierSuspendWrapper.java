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

package org.springframework.cloud.function.context.wrapper;

import java.lang.reflect.Type;
import java.util.function.Supplier;

import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.config.CoroutinesUtils;
import org.springframework.cloud.function.context.config.FunctionUtils;
import org.springframework.core.ResolvableType;
import org.springframework.util.ObjectUtils;


public final class KotlinSupplierSuspendWrapper implements Supplier<Object> {

	public static Boolean isValidSuspendSupplier(Type functionType, Type[] types) {
		return FunctionUtils.isValidKotlinSuspendSupplier(functionType, types);
	}

	public static FunctionRegistration asRegistrationFunction(
		String functionName,
		Object kotlinLambdaTarget,
		Type[] propsTypes
	) {
		KotlinSupplierSuspendWrapper wrapper = new KotlinSupplierSuspendWrapper(kotlinLambdaTarget, functionName);
		FunctionRegistration<?> registration = new FunctionRegistration<>(wrapper, functionName);
		ResolvableType returnType = CoroutinesUtils.getSuspendingFunctionReturnType(propsTypes[0]);
		registration.type(
			ResolvableType.forClassWithGenerics(
				Supplier.class,
				ResolvableType.forClassWithGenerics(Flux.class, returnType)
			).getType()
		);
		return registration;
	}


	private final Object kotlinLambdaTarget;
	private final String name;

	public KotlinSupplierSuspendWrapper(Object kotlinLambdaTarget, String functionName) {
		this.name = functionName;
		this.kotlinLambdaTarget = kotlinLambdaTarget;
	}

	public Object apply(Object input) {
		if (ObjectUtils.isEmpty(input)) {
			return this.get();
		}
		return null;
	}

	@Override
	public Object get() {
		return CoroutinesUtils.invokeSuspendingSupplier(kotlinLambdaTarget);
	}

	public String getName() {
		return this.name;
	}

}
