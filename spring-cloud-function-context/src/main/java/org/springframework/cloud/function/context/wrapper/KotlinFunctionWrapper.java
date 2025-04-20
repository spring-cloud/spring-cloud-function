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



public final class KotlinFunctionWrapper implements Function<Object, Object>, Function1<Object, Object>, Function2<Object, Object, Object> {

	public static Boolean isValidFunction(Type functionType, Type[] types) {
		return FunctionUtils.isValidKotlinFunction(functionType, types);
	}

	public static FunctionRegistration asRegistrationFunction(
		String functionName,
		Object kotlinLambdaTarget,
		Type[] propsTypes
	) {
		Boolean shouldConvertFlowAsFlux = CoroutinesUtils.isFlowType(propsTypes[0]);
		KotlinFunctionWrapper wrapper = new KotlinFunctionWrapper(kotlinLambdaTarget, functionName, shouldConvertFlowAsFlux);
		FunctionRegistration<?> registration = new FunctionRegistration<>(wrapper, functionName);
		if (shouldConvertFlowAsFlux) {
			registration.type(
				ResolvableType.forClassWithGenerics(
					Function.class,
					ResolvableType.forClassWithGenerics(Flux.class, ResolvableType.forType(propsTypes[0])),
					ResolvableType.forClassWithGenerics(Flux.class, ResolvableType.forType(propsTypes[1]))
			).getType()
			);
		}
		else {
			registration.type(
				ResolvableType.forClassWithGenerics(
					Function.class,
					ResolvableType.forType(propsTypes[0]),
					ResolvableType.forType(propsTypes[1])
				).getType()
			);
		}
		return registration;
	}


	private final Object kotlinLambdaTarget;
	private final String name;
	private final Boolean shouldConvertFlowAsFlux;

	public KotlinFunctionWrapper(Object kotlinLambdaTarget, String functionName, Boolean shouldConvertFlowAsFlux) {
		this.kotlinLambdaTarget = kotlinLambdaTarget;
		this.name = functionName;
		this.shouldConvertFlowAsFlux = shouldConvertFlowAsFlux;
	}



	@Override
	public Object invoke(Object arg0, Object arg1) {
		return ((Function2) this.kotlinLambdaTarget).invoke(arg0, arg1);
	}

	@Override
	public Object invoke(Object arg0) {
		if (CoroutinesUtils.isValidFluxFunction(kotlinLambdaTarget, arg0)) {
			return CoroutinesUtils.invokeFluxFunction(kotlinLambdaTarget, arg0, shouldConvertFlowAsFlux);
		}
		if (this.kotlinLambdaTarget instanceof Function1) {
			return ((Function1) this.kotlinLambdaTarget).invoke(arg0);
		}
		else if (this.kotlinLambdaTarget instanceof Function) {
			return ((Function) this.kotlinLambdaTarget).apply(arg0);
		}
		return null;
	}


	public String getName() {
		return this.name;
	}

	@Override
	public Object apply(Object input) {
		return this.invoke(input);
	}
}
