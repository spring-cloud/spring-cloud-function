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

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.config.CoroutinesUtils;
import org.springframework.cloud.function.context.config.FunctionUtils;
import org.springframework.cloud.function.context.config.TypeUtils;
import org.springframework.core.ResolvableType;

/**
 * The KotlinConsumerSuspendFlowWrapper class serves as a bridge for Kotlin suspending
 * consumer functions that process Flow objects, enabling their integration within the
 * Spring Cloud Function framework's reactive programming model.
 *
 * @author Adrien Poupard
 */
public final class KotlinConsumerSuspendFlowWrapper implements KotlinFunctionWrapper, Consumer<Flux<Object>>, Function1<Flux<Object>, Unit> {

	public static Boolean isValid(Type functionType, Type[] types) {
		return FunctionUtils.isValidKotlinSuspendConsumer(functionType, types) && TypeUtils.isFlowType(types[0]);
	}

	public static KotlinConsumerSuspendFlowWrapper asRegistrationFunction(String functionName,
			Object kotlinLambdaTarget, Type[] propsTypes) {
		ResolvableType continuationArgType = TypeUtils.getSuspendingFunctionArgType(propsTypes[0]);
		ResolvableType functionType = ResolvableType.forClassWithGenerics(Consumer.class,
				ResolvableType.forClassWithGenerics(Flux.class, continuationArgType));
		return new KotlinConsumerSuspendFlowWrapper(kotlinLambdaTarget, functionType, functionName);
	}

	private final Object kotlinLambdaTarget;

	private String name;

	private final ResolvableType type;

	public KotlinConsumerSuspendFlowWrapper(Object kotlinLambdaTarget, ResolvableType type, String functionName) {
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
	public void accept(Flux<Object> input) {
		invoke(input);
	}

	@Override
	public Unit invoke(Flux<Object> input) {
		CoroutinesUtils.invokeSuspendingConsumerFlow(kotlinLambdaTarget, input);
		return Unit.INSTANCE;
	}

}
