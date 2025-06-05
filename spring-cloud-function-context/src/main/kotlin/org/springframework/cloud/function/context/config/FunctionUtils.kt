
/*
 * Copyright 2021-2021 the original author or authors.
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

@file:JvmName("FunctionUtils")
package org.springframework.cloud.function.context.config

import java.lang.reflect.Type
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import kotlin.jvm.functions.Function0
import kotlin.jvm.functions.Function1
import kotlin.jvm.functions.Function2
import org.springframework.cloud.function.utils.KotlinUtils

fun isValidKotlinConsumer(functionType: Type, type: Array<Type>): Boolean {
	return isTypeRepresentedByClass(functionType, Consumer::class.java) || (
		isTypeRepresentedByClass(functionType, Function1::class.java) &&
		type.size == 2 &&
		!KotlinUtils.isContinuationType(type[0]) &&
		(KotlinUtils.isUnitType(type[1]) || KotlinUtils.isVoidType(type[1]))
	)
}

fun isValidKotlinSuspendConsumer(functionType: Type, type: Array<Type>): Boolean {
	return isTypeRepresentedByClass(functionType, Function2::class.java) && type.size == 3 &&
		KotlinUtils.isContinuationUnitType(type[1])
}


fun isValidKotlinFunction(functionType: Type, type: Array<Type>): Boolean {
	return (isTypeRepresentedByClass(functionType, Function1::class.java) ||
		isTypeRepresentedByClass(functionType, Function::class.java)) &&
		type.size == 2 && !KotlinUtils.isContinuationType(type[0]) &&
		!KotlinUtils.isUnitType(type[1])
}


fun isValidKotlinSuspendFunction(functionType: Type, type: Array<Type>): Boolean {
	return isTypeRepresentedByClass(functionType, Function2::class.java) && type.size == 3 &&
		KotlinUtils.isContinuationType(type[1]) &&
		!KotlinUtils.isContinuationUnitType(type[1])
}

fun isValidKotlinSupplier(functionType: Type): Boolean {
	return isTypeRepresentedByClass(functionType, Function0::class.java) ||
		isTypeRepresentedByClass(functionType, Supplier::class.java)
}

fun isValidKotlinSuspendSupplier(functionType: Type, type: Array<Type>): Boolean {
	return isTypeRepresentedByClass(functionType, Function1::class.java) && type.size == 2 &&
		KotlinUtils.isContinuationType(type[0])
}

fun isTypeRepresentedByClass(type: Type, clazz: Class<*>): Boolean {
	return type.typeName.contains(clazz.name)
}
