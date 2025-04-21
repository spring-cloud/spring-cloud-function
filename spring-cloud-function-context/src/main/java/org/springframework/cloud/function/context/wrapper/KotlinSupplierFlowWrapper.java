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
import kotlinx.coroutines.flow.Flow;
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.config.FunctionUtils;
import org.springframework.cloud.function.context.config.TypeUtils;
import org.springframework.core.ResolvableType;

import static org.springframework.cloud.function.context.config.TypeUtils.asFlux;

/**
 * @author Adrien Poupard
 *
 */
public final class KotlinSupplierFlowWrapper implements KotlinFunctionWrapper, Supplier<Flux<Object>>, Function0<Flow<Object>> {

	public static Boolean isValid(Type functionType, Type[] types) {
		return FunctionUtils.isValidKotlinSupplier(functionType)
			&& types.length == 1
			&& TypeUtils.isFlowType(types[0]);
	}

	public static KotlinSupplierFlowWrapper asRegistrationFunction(
		String functionName,
		Object kotlinLambdaTarget,
		Type[] propsTypes
	) {
		Function0<Flow<Object>> target = (Function0<Flow<Object>>) kotlinLambdaTarget;

		ResolvableType props = ResolvableType.forClassWithGenerics(Flux.class, ResolvableType.forType(propsTypes[0]));
		ResolvableType functionType = ResolvableType.forClassWithGenerics(Supplier.class, props);

		return new KotlinSupplierFlowWrapper(target, functionType, functionName);
	}


	private final Function0<Flow<Object>> kotlinLambdaTarget;
	private final String name;
	private final ResolvableType type;

	public KotlinSupplierFlowWrapper(Function0<Flow<Object>> kotlinLambdaTarget, ResolvableType type, String functionName) {
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.type = type;
		this.name = functionName;
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
	public Flux<Object> get() {
		Flow<Object> result = invoke();
		return asFlux(result);
	}

	@Override
	public Flow<Object> invoke() {
		return kotlinLambdaTarget.invoke();
	}
}
