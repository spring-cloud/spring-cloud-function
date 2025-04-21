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
import org.springframework.cloud.function.context.config.FunctionUtils;
import org.springframework.cloud.function.context.config.TypeUtils;
import org.springframework.core.ResolvableType;

/**
 * @author Adrien Poupard
 *
 */
public final class KotlinFunctionSuspendFlowToFlowWrapper implements KotlinFunctionWrapper, Function<Flux<Object>, Flux<Object>>, Function1<Flux<Object>, Flux<Object>> {

	public static Boolean isValid(Type functionType, Type[] types) {
		return  FunctionUtils.isValidKotlinSuspendFunction(functionType, types) &&
			types.length == 3 &&
			TypeUtils.isFlowType(types[0]) &&
			TypeUtils.isContinuationFlowType(types[1]);
	}

	public static KotlinFunctionSuspendFlowToFlowWrapper asRegistrationFunction(
		String functionName,
		Object kotlinLambdaTarget,
		Type[] propsTypes
	) {
		ResolvableType props = TypeUtils.getSuspendingFunctionArgType(propsTypes[0]);
		ResolvableType result = TypeUtils.getSuspendingFunctionReturnType(propsTypes[1]);
		ResolvableType functionType = ResolvableType.forClassWithGenerics(
			Function.class,
			ResolvableType.forClassWithGenerics(Flux.class, props),
			ResolvableType.forClassWithGenerics(Flux.class, result)
		);
		return new KotlinFunctionSuspendFlowToFlowWrapper(kotlinLambdaTarget, functionType, functionName);
	}

	private final Object kotlinLambdaTarget;
	private final String name;
	private final ResolvableType type;

	public KotlinFunctionSuspendFlowToFlowWrapper(Object kotlinLambdaTarget, String functionName) {
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.name = functionName;
		this.type = null;
	}

	public KotlinFunctionSuspendFlowToFlowWrapper(Object kotlinLambdaTarget, ResolvableType type, String functionName) {
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.name = functionName;
		this.type = type;
	}

	@Override
	public Flux<Object> invoke(Flux<Object> arg0) {
		Flow<Object> flow = TypeUtils.asFlow(arg0);
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
