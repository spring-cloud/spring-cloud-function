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

import kotlin.jvm.functions.Function0;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.config.FunctionUtils;
import org.springframework.core.ResolvableType;
import org.springframework.util.ObjectUtils;



public final class KotlinSupplierWrapper implements Supplier<Object> {

	public static Boolean isValidSupplier(Type functionType, Type[] types) {
		return FunctionUtils.isValidKotlinSupplier(functionType);
	}

	public static FunctionRegistration asRegistrationFunction(
		String functionName,
		Object kotlinLambdaTarget,
		Type[] propsTypes
	) {
		KotlinSupplierWrapper wrapper = new KotlinSupplierWrapper(kotlinLambdaTarget, functionName);
		FunctionRegistration<?> registration = new FunctionRegistration<>(wrapper, functionName);
		registration.type(
			ResolvableType.forClassWithGenerics(Supplier.class, ResolvableType.forType(propsTypes[0]))
			.getType()
		);
		return registration;
	}

	private final Object kotlinLambdaTarget;
	private final String name;

	public KotlinSupplierWrapper(Object kotlinLambdaTarget, String functionName) {
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
		if (this.kotlinLambdaTarget instanceof kotlin.Function) {
			return ((Function0) this.kotlinLambdaTarget).invoke();
		}
		return ((Supplier) this.kotlinLambdaTarget).get();
	}

	public String getName() {
		return this.name;
	}

}
