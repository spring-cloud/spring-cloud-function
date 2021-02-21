@file:JvmName("CoroutinesUtils")
package org.springframework.cloud.function.context.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.mono
import reactor.core.publisher.Flux
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

fun isValidSuspendingFunction(kotlinLambdaTarget: Any, arg0: Any): Boolean {
	return arg0 is Flux<*> && kotlinLambdaTarget is Function2<*, *, *>
}

fun getSuspendingFunctionArgType(type: Type): Type {
	return getFlowTypeArguments(type)
}

fun getFlowTypeArguments(type: Type): Type {
	if(!isFlowType(type)) {
		return type
	}
	val parameterizedLowerType = type as ParameterizedType
 	if(parameterizedLowerType.actualTypeArguments.isEmpty()) {
		 return parameterizedLowerType
	}

	val actualTypeArgument = parameterizedLowerType.actualTypeArguments[0]
	return if(actualTypeArgument is WildcardType) {
		val wildcardTypeLower = parameterizedLowerType.actualTypeArguments[0] as WildcardType
		wildcardTypeLower.upperBounds[0]
	} else {
		actualTypeArgument
	}
}

fun isFlowType(type: Type): Boolean {
	return type.typeName.startsWith(Flow::class.qualifiedName!!)
}

fun getSuspendingFunctionReturnType(type: Type): Type {
	val lower = getContinuationTypeArguments(type)
	return getFlowTypeArguments(lower)
}

fun isContinuationType(type: Type): Boolean {
	return type.typeName.startsWith(Continuation::class.qualifiedName!!)
}

fun isContinuationUnitType(type: Type): Boolean {
	return isContinuationType(type) && type.typeName.contains(Unit::class.qualifiedName!!)
}

fun isContinuationFlowType(type: Type): Boolean {
	return isContinuationType(type) && type.typeName.contains(Flow::class.qualifiedName!!)
}

private fun getContinuationTypeArguments(type: Type): Type {
	if(!isContinuationType(type)) {
		return type
	}
	val parameterizedType = type as ParameterizedType
	val wildcardType = parameterizedType.actualTypeArguments[0] as WildcardType
	return wildcardType.lowerBounds[0]
}

fun invokeSuspendingFunction(kotlinLambdaTarget: Any, arg0: Any): Flux<Any> {
	val function = kotlinLambdaTarget as SuspendFunction
	val flux = arg0 as Flux<Any>
	return fluxSuspendingFlowFunction(flux, function)
}

fun isValidSuspendingSupplier(kotlinLambdaTarget: Any): Boolean {
	return kotlinLambdaTarget is Function1<*, *>
}

fun invokeSuspendingSupplier(kotlinLambdaTarget: Any): Flux<Any> {
	val supplier = kotlinLambdaTarget as SuspendSupplier
	return mono(Dispatchers.Unconfined) {
		suspendCoroutineUninterceptedOrReturn<Flow<Any>> {
			supplier.invoke(it)
		}
	}.flatMapMany {
		it.asFlux()
	}
}

fun fluxSuspendingFlowFunction(flux: Flux<Any>, target: SuspendFunction): Flux<Any> {
	return mono(Dispatchers.Unconfined) {
		suspendCoroutineUninterceptedOrReturn<Flow<Any>> {
			target.invoke(flux.asFlow(), it)
		}
	}.flatMapMany {
		it.asFlux()
	}
}

private typealias SuspendFunction = (Any?, Any?) -> Any?
private typealias SuspendSupplier = (Any?) -> Any?
