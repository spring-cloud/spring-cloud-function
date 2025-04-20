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
import java.util.function.Function;

import kotlin.jvm.functions.Function1;
import org.springframework.cloud.function.context.config.TypeUtils;
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.config.CoroutinesUtils;
import org.springframework.cloud.function.context.config.FunctionUtils;
import org.springframework.core.ResolvableType;

public final class KotlinConsumerWrapper implements Consumer<Object> {

	public static Boolean isValidConsumer(Type functionType, Type[] types) {
		return FunctionUtils.isValidKotlinConsumer(functionType, types);
	}

	public static FunctionRegistration asRegistrationFunction(
		String functionName,
		Object kotlinLambdaTarget,
		Type[] propsTypes
	) {
		KotlinConsumerWrapper wrapper = new KotlinConsumerWrapper(kotlinLambdaTarget, functionName);
		FunctionRegistration<?> registration = new FunctionRegistration<>(wrapper, functionName);

		if (TypeUtils.isFlowType(propsTypes[0])) {
			registration.type(
				ResolvableType.forClassWithGenerics(
					Consumer.class,
					ResolvableType.forClassWithGenerics(Flux.class, ResolvableType.forType(propsTypes[0]))
				).getType()
			);
		}
		else {
			registration.type(
				ResolvableType.forClassWithGenerics(Consumer.class, ResolvableType.forType(propsTypes[0]))
				.getType()
			);
		}

		return registration;
	}


	private final Object kotlinLambdaTarget;
	private final String name;

	public KotlinConsumerWrapper(Object kotlinLambdaTarget, String functionName) {
		this.name = functionName;
		this.kotlinLambdaTarget = kotlinLambdaTarget;
	}


	public String getName() {
		return this.name;
	}

	@Override
	public void accept(Object input) {
		if (CoroutinesUtils.isValidFluxConsumer(kotlinLambdaTarget, input)) {
			CoroutinesUtils.invokeFluxConsumer(kotlinLambdaTarget, input);
			return;
		}
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
