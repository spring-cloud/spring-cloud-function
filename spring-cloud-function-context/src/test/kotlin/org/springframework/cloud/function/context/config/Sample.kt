package org.springframework.cloud.function.context.config

import kotlin.reflect.javaType
import kotlin.reflect.typeOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

object Sample {
	object Function {
		/**
		 * 1. (T) -> R -> functionSingleToSingle
		 */
		val singleToSingle: (String) -> Int = { it.length }

		/**
		 * 2. (T) -> Flow<R> -> functionSingleToFlow
		 */
		val singleToFlow: (String) -> Flow<String> = { input ->
			flow { input.forEach { emit(it.toString()) } }
		}

		/**
		 * 3. (Flow<T>) -> R -> functionFlowToSingle
		 */
		val flowToSingle: (Flow<String>) -> Int = { flowInput ->
			var count = 0
			runBlocking { flowInput.collect { count++ } }
			count
		}

		/**
		 * 4. (Flow<T>) -> Flow<R> -> functionFlowToFlow
		 */
		val flowToFlow: (Flow<Int>) -> Flow<String> = { inputFlow ->
			inputFlow.map { it.toString() }
		}

		/**
		 * 5. suspend (T) -> R -> functionSuspendSingleToSingle
		 */
		val suspendSingleToSingle: suspend (String) -> Int = { input ->
			input.length
		}

		/**
		 * 6. suspend (T) -> Flow<R> -> functionSuspendSingleToFlow
		 */
		val suspendSingleToFlow: suspend (String) -> Flow<String> = { input ->
			flow { input.forEach { emit(it.toString()) } }
		}

		/**
		 * 7. suspend (Flow<T>) -> R -> functionSuspendFlowToSingle
		 */
		val suspendFlowToSingle: suspend (Flow<String>) -> Int = { flowInput ->
			var count = 0
			flowInput.collect { count++ }
			count
		}

		/**
		 * 8. suspend (Flow<T>) -> Flow<R> -> functionSuspendFlowToFlow
		 */
		val suspendFlowToFlow: suspend (Flow<String>) -> Flow<String> = { flowIn ->
			flow {
				flowIn.collect { emit(it.uppercase()) }
			}
		}
	}

	object Supplier {
		/**
		 * 9. () -> R -> supplierSingle
		 */
		val unitToSingle: () -> Int = { 42 }

		/**
		 * 10. () -> Flow<R> -> supplierFlow
		 */
		val unitToFlow: () -> Flow<String> = {
			flow {
				emit("A")
				emit("B")
			}
		}

		/**
		 * 11. suspend () -> R -> supplierSuspendSingle
		 */
		val suspendUnitToSingle: suspend () -> String = {
			"Hello from suspend"
		}

		/**
		 * 12. suspend () -> Flow<R> -> supplierSuspendFlow
		 */
		val suspendUnitToFlow: suspend () -> Flow<String> = {
			flow {
				emit("x")
				emit("y")
			}
		}
	}

	object Consumer {
		/**
		 *  13. (T) -> Unit -> consumerSingle
		 */
		val singleToUnit: (String) -> Unit = {
			println("Consumed single: $it")
		}

		/**
		 * 14. (Flow<T>) -> Unit -> consumerFlow
		 */
		val flowToUnit: (Flow<String>) -> Unit = { flowIn ->
			runBlocking {
				flowIn.collect { println("Flow item: $it") }
			}
		}

		/**
		 * 15. suspend (T) -> Unit -> suspendConsumer
		 */
		val suspendSingleToUnit: suspend (String) -> Unit = { input ->
			println("Suspend consumed: $input")
		}

		/**
		 *  16. suspend (Flow<T>) -> Unit -> suspendConsumerFlow
		 */
		val suspendFlowToUnit: suspend (Flow<String>) -> Unit = { flowIn ->
			flowIn.collect { println("Flow item: $it") }
		}
	}

	@OptIn(ExperimentalStdlibApi::class)
	object Type {
//		val function: java.lang.reflect.Type = typeOf<suspend (Any) -> Any>().javaType
//		val suspendFunction: java.lang.reflect.Type = typeOf<suspend (Any) -> Any>().javaType
//
//		val Consumer: java.lang.reflect.Type = typeOf<suspend (Any) -> Unit>().javaType
//		val SuspendConsumer: java.lang.reflect.Type = typeOf<suspend (Any) -> Unit>().javaType
//
//		val supplier: java.lang.reflect.Type = typeOf<suspend () -> Any>().javaType
//		val SuspendSupplier: java.lang.reflect.Type = typeOf<suspend () -> Any>().javaType
// Existing:

		val stringType: java.lang.reflect.Type = typeOf<String>().javaType
		val intType: java.lang.reflect.Type = typeOf<Int>().javaType

		val flowStringType: java.lang.reflect.Type = typeOf<Flow<String>>().javaType
		val flowIntType: java.lang.reflect.Type = typeOf<Flow<Int>>().javaType

		val unitType: java.lang.reflect.Type = typeOf<Unit>().javaType

		val continuationStringType: java.lang.reflect.Type =
			typeOf<kotlin.coroutines.Continuation<String>>().javaType

		val continuationIntType: java.lang.reflect.Type =
			typeOf<kotlin.coroutines.Continuation<Int>>().javaType

		val continuationFlowStringType: java.lang.reflect.Type =
			typeOf<kotlin.coroutines.Continuation<Flow<String>>>().javaType

		val continuationUnitType: java.lang.reflect.Type =
			typeOf<kotlin.coroutines.Continuation<Unit>>().javaType
	}

}
