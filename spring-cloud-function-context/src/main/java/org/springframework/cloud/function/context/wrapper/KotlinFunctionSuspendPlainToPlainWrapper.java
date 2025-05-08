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
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.config.CoroutinesUtils;
import org.springframework.cloud.function.context.config.FunctionUtils;
import org.springframework.cloud.function.context.config.TypeUtils;
import org.springframework.core.ResolvableType;

/**
 * The KotlinFunctionSuspendPlainToPlainWrapper class serves as a bridge for Kotlin
 * suspending functions that transform input objects to output objects, enabling their
 * integration within the Spring Cloud Function framework's reactive programming model.
 *
 * @author Adrien Poupard
 */
public final class KotlinFunctionSuspendPlainToPlainWrapper
		implements KotlinFunctionWrapper, Function<Object, Object>, Function1<Object, Object> {

	public static Boolean isValid(Type functionType, Type[] types) {
		return FunctionUtils.isValidKotlinSuspendFunction(functionType, types);
	}

	public static KotlinFunctionSuspendPlainToPlainWrapper asRegistrationFunction(String functionName,
			Object kotlinLambdaTarget, Type[] propsTypes) {
		ResolvableType argType = TypeUtils.getSuspendingFunctionArgType(propsTypes[0]);
		ResolvableType returnType = TypeUtils.getSuspendingFunctionReturnType(propsTypes[1]);
		ResolvableType functionType = ResolvableType.forClassWithGenerics(Function.class, argType,
				ResolvableType.forClassWithGenerics(Flux.class, returnType));
		return new KotlinFunctionSuspendPlainToPlainWrapper(kotlinLambdaTarget, functionType, functionName);
	}

	private final Object kotlinLambdaTarget;

	private String name;

	private final ResolvableType type;

	public KotlinFunctionSuspendPlainToPlainWrapper(Object kotlinLambdaTarget, ResolvableType type,
			String functionName) {
		this.name = functionName;
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.type = type;
	}

	@Override
	public ResolvableType getResolvableType() {
		return type;
	}

	@Override
	public Object invoke(Object arg0) {
		return CoroutinesUtils.invokeSuspendingSingleFunction(kotlinLambdaTarget, arg0);
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
