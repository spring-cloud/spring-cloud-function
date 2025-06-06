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
import kotlinx.coroutines.flow.Flow;
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.config.CoroutinesUtils;
import org.springframework.cloud.function.utils.KotlinUtils;
import org.springframework.core.ResolvableType;

/**
 * The KotlinFunctionSuspendFlowToFlowWrapper class serves as a wrapper for a Kotlin
 * suspending function that consumes a Flow and produces a Flow. It adapts the Kotlin
 * suspending function into a Java {@link Function} and provides support for integration
 * with frameworks requiring reactive streams such as Reactor.
 *
 * @author Adrien Poupard
 */
public final class KotlinFunctionSuspendFlowToFlowWrapper
		implements KotlinFunctionWrapper, Function<Flux<Object>, Flux<Object>>, Function1<Flux<Object>, Flux<Object>> {

	public static Boolean isValid(Type functionType, Type[] types) {
		return KotlinUtils.isValidKotlinSuspendFunction(functionType, types) && types.length == 3
				&& KotlinUtils.isFlowType(types[0]) && KotlinUtils.isContinuationFlowType(types[1]);
	}

	public static KotlinFunctionSuspendFlowToFlowWrapper asRegistrationFunction(String functionName,
			Object kotlinLambdaTarget, Type[] propsTypes) {
		ResolvableType argType = KotlinUtils.getSuspendingFunctionArgType(propsTypes[0]);
		ResolvableType returnType = KotlinUtils.getSuspendingFunctionReturnType(propsTypes[1]);
		ResolvableType functionType = ResolvableType.forClassWithGenerics(Function.class,
				ResolvableType.forClassWithGenerics(Flux.class, argType),
				ResolvableType.forClassWithGenerics(Flux.class, returnType));
		return new KotlinFunctionSuspendFlowToFlowWrapper(kotlinLambdaTarget, functionType, functionName);
	}

	private final Object kotlinLambdaTarget;

	private final String name;

	private final ResolvableType type;

	public KotlinFunctionSuspendFlowToFlowWrapper(Object kotlinLambdaTarget, ResolvableType type, String functionName) {
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.name = functionName;
		this.type = type;
	}

	@Override
	public Flux<Object> invoke(Flux<Object> arg0) {
		Flow<Object> flow = KotlinUtils.convertToFlow(arg0);
		return CoroutinesUtils.invokeSuspendingFlowFunction(kotlinLambdaTarget, flow);
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
	public Flux<Object> apply(Flux<Object> input) {
		return this.invoke(input);
	}

}
