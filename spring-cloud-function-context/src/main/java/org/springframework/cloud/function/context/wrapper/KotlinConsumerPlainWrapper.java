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
import java.util.function.Function;

import kotlin.jvm.functions.Function1;

import org.springframework.cloud.function.context.config.FunctionUtils;
import org.springframework.cloud.function.context.config.TypeUtils;
import org.springframework.core.ResolvableType;

/**
 * @author Adrien Poupard
 *
 */
public final class KotlinConsumerPlainWrapper implements KotlinFunctionWrapper, Consumer<Object> {

	public static Boolean isValid(Type functionType, Type[] types) {
		return FunctionUtils.isValidKotlinConsumer(functionType, types)
			&& !TypeUtils.isFlowType(types[0]);
	}

	public static KotlinConsumerPlainWrapper asRegistrationFunction(
		String functionName,
		Object kotlinLambdaTarget,
		Type[] propsTypes
	) {
		ResolvableType functionType = ResolvableType.forClassWithGenerics(Consumer.class, ResolvableType.forType(propsTypes[0]));
		return new KotlinConsumerPlainWrapper(kotlinLambdaTarget, functionType, functionName);
	}


	private final Object kotlinLambdaTarget;
	private final String name;
	private final ResolvableType type;

	public KotlinConsumerPlainWrapper(Object kotlinLambdaTarget, String functionName) {
		this.name = functionName;
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.type = null;
	}

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
		if (this.kotlinLambdaTarget instanceof Function1) {
			((Function1) this.kotlinLambdaTarget).invoke(input);
		}
		else if (this.kotlinLambdaTarget instanceof Function) {
			((Function) this.kotlinLambdaTarget).apply(input);
		}
		else {
			((Consumer) this.kotlinLambdaTarget).accept(input);
		}
	}

}
