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
 * The KotlinConsumerFlowWrapper class serves as a wrapper for a Kotlin consumer function
 * that consumes a Flow of objects and provides integration with Reactor's Flux API,
 * bridging the gap between Kotlin's Flow and Java's reactive streams.
 *
 * @author Adrien Poupard
 */
public final class KotlinConsumerFlowWrapper
		implements KotlinFunctionWrapper, Consumer<Flux<Object>>, Function1<Flow<Object>, Unit> {

	public static Boolean isValid(Type functionType, Type[] types) {
		return FunctionUtils.isValidKotlinConsumer(functionType, types) && TypeUtils.isFlowType(types[0]);
	}

	public static KotlinConsumerFlowWrapper asRegistrationFunction(String functionName, Object kotlinLambdaTarget,
			Type[] propsTypes) {
		ResolvableType props = ResolvableType.forClassWithGenerics(Flux.class, ResolvableType.forType(propsTypes[0]));
		ResolvableType functionType = ResolvableType.forClassWithGenerics(Consumer.class, props);
		return new KotlinConsumerFlowWrapper(kotlinLambdaTarget, functionType, functionName);
	}

	private final Object kotlinLambdaTarget;

	private final String name;

	private final ResolvableType type;

	public KotlinConsumerFlowWrapper(Object kotlinLambdaTarget, ResolvableType type,
			String functionName) {
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
	public void accept(Flux<Object> props) {
		Flow<Object> flow = TypeUtils.convertToFlow(props);
		invoke(flow);
	}

	@Override
	public Unit invoke(Flow<Object> props) {
		if (kotlinLambdaTarget instanceof Function1) {
			Function1<Flow<Object>, Unit> function = (Function1<Flow<Object>, Unit>) kotlinLambdaTarget;
			return function.invoke(props);
		}
		else if (kotlinLambdaTarget instanceof Consumer) {
			Consumer<Flow<Object>> target = (Consumer<Flow<Object>>) kotlinLambdaTarget;
			target.accept(props);
			return Unit.INSTANCE;
		}
		else {
			throw new IllegalArgumentException("Unsupported target type: " + kotlinLambdaTarget.getClass());
		}
	}

}
