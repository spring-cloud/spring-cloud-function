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

import org.springframework.cloud.function.utils.KotlinUtils;
import org.springframework.core.ResolvableType;

/**
 * The KotlinFunctionFlowToPlainWrapper class serves as a bridge for Kotlin functions that
 * take Flow objects as input and produce regular objects as output, enabling their
 * integration within the Spring Cloud Function framework's reactive programming model.
 *
 * @author Adrien Poupard
 */
public final class KotlinFunctionFlowToPlainWrapper
		implements KotlinFunctionWrapper, Function<Flux<Object>, Object>, Function1<Flux<Object>, Object> {

	public static Boolean isValid(Type functionType, Type[] types) {
		return KotlinUtils.isValidKotlinFunction(functionType, types) && types.length == 2
				&& KotlinUtils.isFlowType(types[0]) && !KotlinUtils.isFlowType(types[1]);
	}

	public static KotlinFunctionFlowToPlainWrapper asRegistrationFunction(String functionName,
			Object kotlinLambdaTarget, Type[] propsTypes) {
		ResolvableType props = ResolvableType.forClassWithGenerics(Flux.class, ResolvableType.forType(propsTypes[0]));
		ResolvableType result = ResolvableType.forType(propsTypes[1]);
		ResolvableType functionType = ResolvableType.forClassWithGenerics(Function.class, props, result);
		return new KotlinFunctionFlowToPlainWrapper(kotlinLambdaTarget, functionType, functionName);
	}

	private final Object kotlinLambdaTarget;

	private final String name;

	private final ResolvableType type;

	public KotlinFunctionFlowToPlainWrapper(Object kotlinLambdaTarget, ResolvableType type, String functionName) {
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.name = functionName;
		this.type = type;
	}

	@Override
	public Object invoke(Flux<Object> arg0) {
		Flow<Object> flow = KotlinUtils.convertToFlow(arg0);
		if (kotlinLambdaTarget instanceof Function<?, ?>) {
			Function<Flow<Object>, Object> target = (Function<Flow<Object>, Object>) kotlinLambdaTarget;
			return target.apply(flow);
		}
		else if (kotlinLambdaTarget instanceof Function1) {
			Function1<Flow<Object>, Object> target = (Function1<Flow<Object>, Object>) kotlinLambdaTarget;
			return target.invoke(flow);
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
	public Object apply(Flux<Object> input) {
		return this.invoke(input);
	}

}
