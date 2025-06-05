/*
 * Copyright 2025-2025 the original author or authors.
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

import org.springframework.cloud.function.utils.KotlinUtils;
import org.springframework.core.ResolvableType;
import org.springframework.util.ObjectUtils;

/**
 * The KotlinSupplierPlainWrapper class serves as a bridge for Kotlin supplier functions
 * that return regular objects, enabling their integration within the Spring Cloud
 * Function framework.
 *
 * @author Adrien Poupard
 *
 */
public final class KotlinSupplierPlainWrapper implements KotlinFunctionWrapper, Supplier<Object>, Function0<Object> {

	public static Boolean isValid(Type functionType, Type[] types) {
		return KotlinUtils.isValidKotlinSupplier(functionType);
	}

	public static KotlinSupplierPlainWrapper asRegistrationFunction(String functionName, Object kotlinLambdaTarget,
			Type[] propsTypes) {
		ResolvableType functionType = ResolvableType.forClassWithGenerics(Supplier.class,
				ResolvableType.forType(propsTypes[0]));
		return new KotlinSupplierPlainWrapper(kotlinLambdaTarget, functionType, functionName);
	}

	private final Object kotlinLambdaTarget;

	private final String name;

	private final ResolvableType type;

	public KotlinSupplierPlainWrapper(Object kotlinLambdaTarget, ResolvableType type, String functionName) {
		this.name = functionName;
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.type = type;
	}

	public Object apply(Object input) {
		if (ObjectUtils.isEmpty(input)) {
			return this.get();
		}
		return null;
	}

	@Override
	public Object get() {
		return invoke();
	}

	@Override
	public Object invoke() {
		if (this.kotlinLambdaTarget instanceof Function0) {
			return ((Function0<?>) this.kotlinLambdaTarget).invoke();
		}
		else if (this.kotlinLambdaTarget instanceof Supplier) {
			return ((Supplier<?>) this.kotlinLambdaTarget).get();
		}
		else {
			throw new IllegalArgumentException("Unsupported target type: " + kotlinLambdaTarget.getClass());
		}
	}

	@Override
	public ResolvableType getResolvableType() {
		return type;
	}

	@Override
	public String getName() {
		return this.name;
	}

}
