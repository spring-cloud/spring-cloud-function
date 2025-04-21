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
import kotlinx.coroutines.flow.Flow;
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.config.FunctionUtils;
import org.springframework.cloud.function.context.config.TypeUtils;
import org.springframework.core.ResolvableType;

/**
 * @author Adrien Poupard
 *
 */
public final class KotlinConsumerFlowWrapper implements KotlinFunctionWrapper, Consumer<Flux<Object>>, Function1<Flux<Object>, Unit> {

	public static Boolean isValid(Type functionType, Type[] types) {
		return FunctionUtils.isValidKotlinConsumer(functionType, types)
			&& TypeUtils.isFlowType(types[0]);
	}

	public static KotlinConsumerFlowWrapper asRegistrationFunction(
		String functionName,
		Object kotlinLambdaTarget,
		Type[] propsTypes
	) {
		Function1<Flow<Object>, Unit> target = (Function1<Flow<Object>, Unit>) kotlinLambdaTarget;
		ResolvableType props = ResolvableType.forClassWithGenerics(Flux.class, ResolvableType.forType(propsTypes[0]));
		ResolvableType functionType = ResolvableType.forClassWithGenerics(Consumer.class, props);
		return new KotlinConsumerFlowWrapper(target, functionType, functionName);
	}


	private final Function1<Flow<Object>, Unit> kotlinLambdaTarget;
	private final String name;
	private final ResolvableType type;

	public KotlinConsumerFlowWrapper(Function1<Flow<Object>, Unit> kotlinLambdaTarget, String functionName) {
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.name = functionName;
		this.type = null;
	}

	public KotlinConsumerFlowWrapper(Function1<Flow<Object>, Unit> kotlinLambdaTarget, ResolvableType type, String functionName) {
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
	public void accept(Flux<Object> o) {
		invoke(o);
	}

	@Override
	public Unit invoke(Flux<Object> o) {
		Flow<Object> props = TypeUtils.asFlow(o);
		return kotlinLambdaTarget.invoke(props);
	}
}
