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
import java.util.function.Function;

import kotlin.jvm.functions.Function1;

import org.springframework.cloud.function.context.config.FunctionUtils;
import org.springframework.core.ResolvableType;

/**
 * The KotlinFunctionObjectToObjectWrapper class serves as a wrapper for Kotlin functions,
 * enabling seamless integration between Kotlin's functional types and Java's Function
 * interface within the Spring Cloud Function framework.
 *
 * @author Adrien Poupard
 */
public final class KotlinFunctionPlainToPlainWrapper
		implements KotlinFunctionWrapper, Function<Object, Object>, Function1<Object, Object> {

	public static Boolean isValid(Type functionType, Type[] types) {
		return FunctionUtils.isValidKotlinFunction(functionType, types);
	}

	public static KotlinFunctionPlainToPlainWrapper asRegistrationFunction(String functionName,
			Object kotlinLambdaTarget, Type[] propsTypes) {
		ResolvableType type = ResolvableType.forClassWithGenerics(Function.class, ResolvableType.forType(propsTypes[0]),
				ResolvableType.forType(propsTypes[1]));
		return new KotlinFunctionPlainToPlainWrapper(kotlinLambdaTarget, type, functionName);
	}

	private final Object kotlinLambdaTarget;

	private final String name;

	private final ResolvableType type;

	public KotlinFunctionPlainToPlainWrapper(Object kotlinLambdaTarget, ResolvableType type, String functionName) {
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.name = functionName;
		this.type = type;
	}

	@Override
	public Object invoke(Object arg0) {
		if (this.kotlinLambdaTarget instanceof Function1) {
			return ((Function1<Object, Object>) this.kotlinLambdaTarget).invoke(arg0);
		}
		else if (this.kotlinLambdaTarget instanceof Function) {
			return ((Function) this.kotlinLambdaTarget).apply(arg0);
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

	@Override
	public Object apply(Object input) {
		return this.invoke(input);
	}

}
