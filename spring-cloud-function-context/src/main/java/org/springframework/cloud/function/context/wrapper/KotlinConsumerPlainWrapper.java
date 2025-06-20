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

import org.springframework.cloud.function.utils.KotlinUtils;
import org.springframework.core.ResolvableType;

/**
 * The KotlinConsumerPlainWrapper class serves as a bridge for Kotlin consumer functions
 * that process regular objects, enabling their integration within the Spring Cloud
 * Function framework.
 *
 * @author Adrien Poupard
 */
public final class KotlinConsumerPlainWrapper implements KotlinFunctionWrapper, Consumer<Object>, Function1<Object, Unit> {

	public static Boolean isValid(Type functionType, Type[] types) {
		return KotlinUtils.isValidKotlinConsumer(functionType, types) && !KotlinUtils.isFlowType(types[0]);
	}

	public static KotlinConsumerPlainWrapper asRegistrationFunction(String functionName, Object kotlinLambdaTarget,
			Type[] propsTypes) {
		ResolvableType functionType = ResolvableType.forClassWithGenerics(
			Consumer.class,
			ResolvableType.forType(propsTypes[0])
		);
		return new KotlinConsumerPlainWrapper(kotlinLambdaTarget, functionType, functionName);
	}

	private final Object kotlinLambdaTarget;

	private final String name;

	private final ResolvableType type;

	public KotlinConsumerPlainWrapper(Object kotlinLambdaTarget, ResolvableType type, String functionName) {
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
		invoke(input);
	}

	@Override
	public Unit invoke(Object input) {
		if (this.kotlinLambdaTarget instanceof Function1) {
			// Call the function but don't try to cast the result to Unit
			// This handles cases where the function returns something other than Unit (e.g., MonoRunnable)
			((Function1) this.kotlinLambdaTarget).invoke(input);
			return Unit.INSTANCE;
		}
		else if (this.kotlinLambdaTarget instanceof Consumer) {
			((Consumer<Object>) this.kotlinLambdaTarget).accept(input);
			return Unit.INSTANCE;
		}
		else {
			throw new IllegalArgumentException("Unsupported target type: " + kotlinLambdaTarget.getClass());
		}
	}

}
