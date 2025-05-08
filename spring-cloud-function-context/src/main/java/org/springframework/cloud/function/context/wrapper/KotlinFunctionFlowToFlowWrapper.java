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

import org.springframework.cloud.function.context.config.FunctionUtils;
import org.springframework.cloud.function.context.config.TypeUtils;
import org.springframework.core.ResolvableType;

/**
 * The KotlinFunctionFlowToFlowWrapper class serves as a bridge for Kotlin functions that
 * process Flow objects, converting both input and output between Kotlin's Flow and Java's
 * Flux for seamless integration with Spring Cloud Function's reactive programming model.
 *
 * @author Adrien Poupard
 */
public final class KotlinFunctionFlowToFlowWrapper
		implements KotlinFunctionWrapper, Function<Flux<Object>, Flux<Object>>, Function1<Flux<Object>, Flux<Object>> {

	public static Boolean isValid(Type functionType, Type[] types) {
		return FunctionUtils.isValidKotlinFunction(functionType, types) && types.length == 2
				&& TypeUtils.isFlowType(types[0]) && TypeUtils.isFlowType(types[1]);
	}

	public static KotlinFunctionFlowToFlowWrapper asRegistrationFunction(String functionName, Object kotlinLambdaTarget,
			Type[] propsTypes) {
		ResolvableType props = ResolvableType.forClassWithGenerics(Flux.class, ResolvableType.forType(propsTypes[0]));
		ResolvableType result = ResolvableType.forClassWithGenerics(Flux.class, ResolvableType.forType(propsTypes[1]));
		ResolvableType functionType = ResolvableType.forClassWithGenerics(Function.class, props, result);
		return new KotlinFunctionFlowToFlowWrapper(
			kotlinLambdaTarget,
			functionType,
			functionName
		);
	}

	private final Object kotlinLambdaTarget;

	private final String name;

	private final ResolvableType type;

	public KotlinFunctionFlowToFlowWrapper(Object kotlinLambdaTarget, ResolvableType type, String functionName) {
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.name = functionName;
		this.type = type;
	}

	@Override
	public Flux<Object> apply(Flux<Object> input) {
		return this.invoke(input);
	}

	@Override
	public Flux<Object> invoke(Flux<Object> arg0) {
		Flow<Object> flow = TypeUtils.convertToFlow(arg0);
		if (kotlinLambdaTarget instanceof Function1) {
			Function1<Flow<Object>, Flow<Object>> target = (Function1<Flow<Object>, Flow<Object>>) kotlinLambdaTarget;
			Flow<Object> result = target.invoke(flow);
			return TypeUtils.convertToFlux(result);
		}
		else if (kotlinLambdaTarget instanceof Function<?, ?>) {
			Function<Flow<Object>, Flow<Object>> target = (Function<Flow<Object>, Flow<Object>>) kotlinLambdaTarget;
			Flow<Object> result = target.apply(flow);
			return TypeUtils.convertToFlux(result);
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
