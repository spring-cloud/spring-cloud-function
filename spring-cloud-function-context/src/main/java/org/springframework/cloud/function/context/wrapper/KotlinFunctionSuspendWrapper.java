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
import java.util.function.Function;

import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.config.CoroutinesUtils;
import org.springframework.cloud.function.context.config.FunctionUtils;
import org.springframework.core.ResolvableType;


public final class KotlinFunctionSuspendWrapper implements Function<Object, Object>, Function1<Object, Object>, Function2<Object, Object, Object> {

	public static Boolean isValidSuspendFunction(Type functionType, Type[] types) {
		return FunctionUtils.isValidKotlinSuspendFunction(functionType, types);
	}

	public static FunctionRegistration asRegistrationFunction(
		String functionName,
		Object kotlinLambdaTarget,
		Type[] propsTypes
	) {
		ResolvableType agType = CoroutinesUtils.getSuspendingFunctionArgType(propsTypes[0]);
		ResolvableType returnType = CoroutinesUtils.getSuspendingFunctionReturnType(propsTypes[1]);
		Boolean shouldConvertFlowAsFlux = CoroutinesUtils.isFlowType(propsTypes[0]);
		KotlinFunctionSuspendWrapper wrapper = new KotlinFunctionSuspendWrapper(kotlinLambdaTarget, functionName, shouldConvertFlowAsFlux);
		FunctionRegistration<?> registration = new FunctionRegistration<>(wrapper, functionName);
		if (shouldConvertFlowAsFlux) {
			registration.type(
				ResolvableType.forClassWithGenerics(
					Function.class,
					ResolvableType.forClassWithGenerics(Flux.class, agType),
					ResolvableType.forClassWithGenerics(Flux.class, returnType)
				).getType()
			);
		}
		else {
			registration.type(
				ResolvableType.forClassWithGenerics(
					Function.class,
					agType,
					ResolvableType.forClassWithGenerics(Flux.class, returnType)
				).getType()
			);
		}
		return registration;
	}


	private final Object kotlinLambdaTarget;
	private String name;
	private Boolean shouldConvertFlowAsFlux;

	public KotlinFunctionSuspendWrapper(Object kotlinLambdaTarget, String functionName, Boolean shouldConvertFlowAsFlux) {
		this.name = functionName;
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.shouldConvertFlowAsFlux = shouldConvertFlowAsFlux;
	}



	@Override
	public Object invoke(Object arg0, Object arg1) {
		return ((Function2) this.kotlinLambdaTarget).invoke(arg0, arg1);
	}

	@Override
	public Object invoke(Object arg0) {
		if (CoroutinesUtils.isValidFluxFunction(kotlinLambdaTarget, arg0)) {
			return CoroutinesUtils.invokeSuspendingFluxFunction(kotlinLambdaTarget, arg0, shouldConvertFlowAsFlux);
		}
		else {
			return CoroutinesUtils.invokeSuspendingSingleFunction(kotlinLambdaTarget, arg0);
		}
	}

	public String getName() {
		return this.name;
	}

	@Override
	public Object apply(Object input) {
		return this.invoke(input);
	}
}
