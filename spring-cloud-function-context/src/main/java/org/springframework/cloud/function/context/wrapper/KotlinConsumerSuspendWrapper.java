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
import java.util.function.Consumer;

import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.config.CoroutinesUtils;
import org.springframework.cloud.function.context.config.FunctionUtils;
import org.springframework.cloud.function.context.config.TypeUtils;
import org.springframework.core.ResolvableType;

/**
 * @author Adrien Poupard
 *
 */
public final class KotlinConsumerSuspendWrapper implements KotlinFunctionWrapper, Consumer<Object> {

	public static Boolean isValid(Type functionType, Type[] types) {
		return FunctionUtils.isValidKotlinSuspendConsumer(functionType, types);
	}

	public static KotlinConsumerSuspendWrapper asRegistrationFunction(
		String functionName,
		Object kotlinLambdaTarget,
		Type[] propsTypes
	) {
		ResolvableType continuationArgType = TypeUtils.getSuspendingFunctionArgType(propsTypes[0]);
		ResolvableType functionType = ResolvableType.forClassWithGenerics(
			Consumer.class,
			ResolvableType.forClassWithGenerics(Flux.class, continuationArgType)
		);
		return new KotlinConsumerSuspendWrapper(kotlinLambdaTarget, functionType, functionName);
	}

	private final Object kotlinLambdaTarget;
	private String name;
	private final ResolvableType type;

	public KotlinConsumerSuspendWrapper(Object kotlinLambdaTarget, String functionName) {
		this.name = functionName;
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.type = null;
	}

	public KotlinConsumerSuspendWrapper(Object kotlinLambdaTarget, ResolvableType type, String functionName) {
		this.name = functionName;
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.type = type;
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
	public void accept(Object input) {
		CoroutinesUtils.invokeSuspendingConsumer(kotlinLambdaTarget, input);
	}
}
