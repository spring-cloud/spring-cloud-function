@file:JvmName("CoroutinesUtils")
package org.springframework.cloud.function.context.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.mono
import reactor.core.publisher.Flux
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn


fun getSuspendingFunctionReturnType(type: Type): Type? {
	val parameterizedType = type as ParameterizedType
	val wildcardType = parameterizedType.actualTypeArguments[0] as WildcardType
	return wildcardType.lowerBounds[0]
}


fun isValidSuspendingFunction(arg0: Any, kotlinLambdaTarget: Any): Boolean {
	return arg0 is Flux<*> && kotlinLambdaTarget is Function2<*, *, *>
}

fun invokeSuspendingFunction(arg0: Any, kotlinLambdaTarget: Any): Flux<Any> {
	val function = kotlinLambdaTarget as SuspendFunction
	val flux = arg0 as Flux<Any>
	return fluxSuspendingFunction(flux, function)
}

fun fluxSuspendingFunction(flux: Flux<Any>, target: SuspendFunction): Flux<Any> {
	return flux.flatMap { message ->
		mono(Dispatchers.Unconfined) {
			suspendCoroutineUninterceptedOrReturn {
				target.invoke(message, it)
			}
		}
	}
}

typealias SuspendFunction = (Any?, Any?) -> Any?
