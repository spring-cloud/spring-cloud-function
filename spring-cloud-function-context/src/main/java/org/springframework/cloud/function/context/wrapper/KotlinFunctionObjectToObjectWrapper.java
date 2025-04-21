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

import org.springframework.cloud.function.context.config.CoroutinesUtils;
import org.springframework.cloud.function.context.config.FunctionUtils;
import org.springframework.core.ResolvableType;

/**
 * @author Adrien Poupard
 *
 */
public final class KotlinFunctionObjectToObjectWrapper implements KotlinFunctionWrapper, Function<Object, Object>, Function1<Object, Object> {

	public static Boolean isValid(Type functionType, Type[] types) {
		return FunctionUtils.isValidKotlinFunction(functionType, types);
	}

	public static KotlinFunctionObjectToObjectWrapper asRegistrationFunction(
		String functionName,
		Object kotlinLambdaTarget,
		Type functionType,
		Type[] propsTypes
	) {
		Boolean isSuspendFunction = FunctionUtils.isValidKotlinSuspendFunction(functionType, propsTypes);
		ResolvableType type = ResolvableType.forClassWithGenerics(
			Function.class,
			ResolvableType.forType(propsTypes[0]),
			ResolvableType.forType(propsTypes[1])
		);
		return new KotlinFunctionObjectToObjectWrapper(kotlinLambdaTarget, type, functionName, isSuspendFunction);
	}


	private final Object kotlinLambdaTarget;
	private final String name;
	private final Boolean isSuspendFunction;
	private final ResolvableType type;

	public KotlinFunctionObjectToObjectWrapper(Object kotlinLambdaTarget, String functionName, Boolean isSuspendFunction) {
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.name = functionName;
		this.isSuspendFunction = isSuspendFunction;
		this.type = null;
	}

	public KotlinFunctionObjectToObjectWrapper(Object kotlinLambdaTarget, ResolvableType type, String functionName, Boolean isSuspendFunction) {
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.name = functionName;
		this.isSuspendFunction = isSuspendFunction;
		this.type = type;
	}

	@Override
	public Object invoke(Object arg0) {
		if (this.isSuspendFunction) {
			return CoroutinesUtils.invokeSuspendingSingleFunction(kotlinLambdaTarget, arg0);
		}
		else if (this.kotlinLambdaTarget instanceof Function1) {
			return ((Function1<Object, Object>) this.kotlinLambdaTarget).invoke(arg0);
		}
		else if (this.kotlinLambdaTarget instanceof Function) {
			return ((Function) this.kotlinLambdaTarget).apply(arg0);
		}
		return null;
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
