/*
 * Copyright 2012-2021 the original author or authors.
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

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.config.CoroutinesUtils;
import org.springframework.cloud.function.context.config.FunctionUtils;
import org.springframework.core.ResolvableType;

public final class KotlinConsumerSuspendWrapper implements Consumer<Object> {

	public static Boolean isValidSuspendConsumer(Type functionType, Type[] types) {
		return FunctionUtils.isValidKotlinSuspendConsumer(functionType, types);
	}

	public static FunctionRegistration asRegistrationFunction(
		String functionName,
		Object kotlinLambdaTarget,
		Type[] propsTypes
	) {
		KotlinConsumerSuspendWrapper wrapper = new KotlinConsumerSuspendWrapper(kotlinLambdaTarget, functionName);
		FunctionRegistration<?> registration = new FunctionRegistration<>(wrapper, functionName);

		ResolvableType continuationArgType = CoroutinesUtils.getSuspendingFunctionArgType(propsTypes[0]);
		registration.type(
			ResolvableType.forClassWithGenerics(
				Consumer.class,
				ResolvableType.forClassWithGenerics(Flux.class, continuationArgType)
			).getType()
		);

		return registration;
	}


	private final Object kotlinLambdaTarget;
	private String name;

	public KotlinConsumerSuspendWrapper(Object kotlinLambdaTarget, String functionName) {
		this.name = functionName;
		this.kotlinLambdaTarget = kotlinLambdaTarget;
	}


	public String getName() {
		return this.name;
	}


	@Override
	public void accept(Object input) {
		CoroutinesUtils.invokeSuspendingConsumer(kotlinLambdaTarget, input);
	}
}
