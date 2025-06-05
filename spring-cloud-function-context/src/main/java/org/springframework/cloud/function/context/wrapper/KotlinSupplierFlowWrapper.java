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
import org.springframework.cloud.function.utils.KotlinUtils;
import org.springframework.core.ResolvableType;


/**
 * The KotlinSupplierFlowWrapper class serves as a wrapper to integrate Kotlin's Function0
 * with Java's Supplier interface and transform Kotlin Flow objects to Reactor Flux
 * objects, bridging functional paradigms between Kotlin and Java within the Spring Cloud
 * Function framework.
 *
 * @author Adrien Poupard
 */
public final class KotlinSupplierFlowWrapper
		implements KotlinFunctionWrapper, Supplier<Flux<Object>>, Function0<Flow<Object>> {

	public static Boolean isValid(Type functionType, Type[] types) {
		return FunctionUtils.isValidKotlinSupplier(functionType) && types.length == 1 && KotlinUtils.isFlowType(types[0]);
	}

	public static KotlinSupplierFlowWrapper asRegistrationFunction(String functionName, Object kotlinLambdaTarget,
			Type[] propsTypes) {

		ResolvableType props = ResolvableType.forClassWithGenerics(Flux.class, ResolvableType.forType(propsTypes[0]));
		ResolvableType functionType = ResolvableType.forClassWithGenerics(Supplier.class, props);

		return new KotlinSupplierFlowWrapper(kotlinLambdaTarget, functionType, functionName);
	}

	private final Object kotlinLambdaTarget;

	private final String name;

	private final ResolvableType type;

	public KotlinSupplierFlowWrapper(Object kotlinLambdaTarget, ResolvableType type, String functionName) {
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
		return KotlinUtils.convertToFlux(result);
	}

	@Override
	public Flow<Object> invoke() {
		if (kotlinLambdaTarget instanceof Function0) {
			Function0<Flow<Object>> target = (Function0<Flow<Object>>) kotlinLambdaTarget;
			Flow<Object> result = target.invoke();
			return result;
		}
		else if (kotlinLambdaTarget instanceof Supplier) {
			Supplier<Flow<Object>> target = (Supplier<Flow<Object>>) kotlinLambdaTarget;
			Flow<Object> result = target.get();
			return result;
		}
		else {
			throw new IllegalArgumentException("Unsupported target type: " + kotlinLambdaTarget.getClass());
		}
	}
}
