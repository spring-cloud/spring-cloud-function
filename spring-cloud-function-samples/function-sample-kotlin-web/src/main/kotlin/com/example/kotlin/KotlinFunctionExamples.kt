package com.example.kotlin

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * ## List of Combinations Tested (in requested order):
 *
 *  1. **(T) -> R**                   -> functionSingleToSingle
 *  2. **(T) -> Flow<R>**             -> functionSingleToFlow
 *  3. **(Flow<T>) -> R**             -> functionFlowToSingle
 *  4. **(Flow<T>) -> Flow<R>**       -> functionFlowToFlow
 *  5. **suspend (T) -> R**           -> suspendFunctionSingleToSingle
 *  6. **suspend (T) -> Flow<R>**     -> suspendFunctionSingleToFlow
 *  7. **suspend (Flow<T>) -> R**     -> suspendFunctionFlowToSingle   <-- Changed here
 *  8. **suspend (Flow<T>) -> Flow<R>** -> suspendFunctionFlowToFlow
 *  9. **() -> R**                    -> supplierSingle
 *  10. **() -> Flow<R>**             -> supplierFlow
 *  11. **suspend () -> R**           -> suspendSupplier
 *  12. **suspend () -> Flow<R>**     -> suspendSupplierFlow
 *  13. **(T) -> Unit**               -> consumerSingle
 *  14. **(Flow<T>) -> Unit**         -> consumerFlow
 *  15. **suspend (T) -> Unit**       -> suspendConsumer
 *  16. **suspend (Flow<T>) -> Unit** -> suspendConsumerFlow
 *
 */
@Configuration
class KotlinFunctionExamples {

	// 1) (T) -> R
	/**
	 * Takes a String and returns its length (Int).
	 *
	 * **Example:**
	 * Input: "Hello"
	 * Output: 5
	 */
	@Bean
	fun functionSingleToSingle(): (String) -> Int = { input ->
		input.length
	}

	// 2) (T) -> Flow<R>
	/**
	 * Takes a String and returns a Flow of its characters.
	 *
	 * **Example:**
	 * Input: "test"
	 * Output: Flow("t","e","s","t")
	 */
	@Bean
	fun functionSingleToFlow(): (String) -> Flow<String> = { input ->
		flow {
			input.forEach { c -> emit(c.toString()) }
		}
	}

	// 3) (Flow<T>) -> R
	/**
	 * Takes a Flow<String> and returns an Int count of elements (blocking).
	 *
	 * **Example:**
	 * Input: Flow("one","two","three")
	 * Output: 3
	 */
	@Bean
	fun functionFlowToSingle(): (Flow<String>) -> Int = { flowInput ->
		var count = 0
		runBlocking {
			flowInput.collect { count++ }
		}
		count
	}

	// 4) (Flow<T>) -> Flow<R>
	/**
	 * Takes a Flow<Int> and returns a Flow<String>.
	 *
	 * **Example:**
	 * Input: Flow(1,2,3)
	 * Output: Flow("1","2","3")
	 */
	@Bean
	fun functionFlowToFlow(): (Flow<Int>) -> Flow<String> = { flowInput ->
		flowInput.map { it.toString() }
	}

	// 5) suspend (T) -> R
	/**
	 * Suspending function that takes a String and returns its length (Int).
	 *
	 * **Example:**
	 * Input: "kotlin"
	 * Output: 6
	 */
	@Bean
	fun suspendFunctionSingleToSingle(): suspend (String) -> Int = { input ->
		delay(100)
		input.length
	}

	// 6) suspend (T) -> Flow<R>
	/**
	 * Suspending function that takes a String, returns a Flow of its characters.
	 *
	 * **Example:**
	 * Input: "demo"
	 * Output: Flow("d","e","m","o")
	 */
	@Bean
	fun suspendFunctionSingleToFlow(): suspend (String) -> Flow<String> = { input ->
		flow {
			delay(100)
			input.forEach { c -> emit(c.toString()) }
		}
	}

	// 7) suspend (Flow<T>) -> R
	/**
	 * Suspending function that takes a Flow<String> and returns an Int (count of items).
	 *
	 * **Example:**
	 * Input: Flow("alpha","beta")
	 * Output: 2
	 */
	@Bean
	fun suspendFunctionFlowToSingle(): suspend (Flow<String>) -> Int = { flowInput ->
		var count = 0
		flowInput.collect { count++ }
		count
	}

	// 8) suspend (Flow<T>) -> Flow<R>
	/**
	 * Suspending function that takes a Flow<String> and returns another Flow<String>.
	 *
	 * **Example:**
	 * Input: Flow("abc","xyz")
	 * Output: Flow("ABC","XYZ") (uppercase)
	 */
	@Bean
	fun suspendFunctionFlowToFlow(): suspend (Flow<String>) -> Flow<String> = { incomingFlow ->
		flow {
			delay(100)
			incomingFlow.collect { item ->
				emit(item.uppercase())
			}
		}
	}

	// 9) () -> R
	/**
	 * Supplier that returns an Int (no input).
	 *
	 * **Example:**
	 * Output: 42
	 */
	@Bean
	fun supplierSingle(): () -> Int = {
		42
	}

	// 10) () -> Flow<R>
	/**
	 * Supplier that returns a Flow of Strings (no input).
	 *
	 * **Example:**
	 * Output: Flow("A","B","C")
	 */
	@Bean
	fun supplierFlow(): () -> Flow<String> = {
		flow {
			emit("A")
			emit("B")
			emit("C")
		}
	}

	// 11) suspend () -> R
	/**
	 * Suspending supplier that returns a String (no input).
	 *
	 * **Example:**
	 * Output: "Hello from suspend"
	 */
	@Bean
	fun suspendSupplier(): suspend () -> String = {
		delay(100)
		"Hello from suspend"
	}

	// 12) suspend () -> Flow<R>
	/**
	 * Suspending supplier that returns a Flow of Strings (no input).
	 *
	 * **Example:**
	 * Output: Flow("x","y","z")
	 */
	@Bean
	fun suspendSupplierFlow(): suspend () -> Flow<String> = {
		flow {
			delay(100)
			emit("x")
			emit("y")
			emit("z")
		}
	}

	// 13) (T) -> Unit
	/**
	 * Consumer that takes a String (side-effect only).
	 *
	 * **Example:**
	 * Input: "Log me"
	 * Output: (prints "Consumed: Log me")
	 */
	@Bean
	fun consumerSingle(): (String) -> Unit = { input ->
		println("Consumed: $input")
	}

	// 14) (Flow<T>) -> Unit
	/**
	 * Consumer that takes a Flow<String> (side-effect only).
	 *
	 * **Example:**
	 * Input: Flow("one","two")
	 * Output: (collects and logs each element)
	 */
	@Bean
	fun consumerFlow(): (Flow<String>) -> Unit = { flowInput ->
		println("Received flow: $flowInput (would collect in coroutine)")
	}

	// 15) suspend (T) -> Unit
	/**
	 * Suspending consumer that takes a String (side-effect only).
	 *
	 * **Example:**
	 * Input: "test"
	 * Output: (logs after some delay)
	 */
	@Bean
	fun suspendConsumer(): suspend (String) -> Unit = { input ->
		delay(100)
		println("Suspend consumed: $input")
	}

	// 16) suspend (Flow<T>) -> Unit
	/**
	 * Suspending consumer that takes a Flow<String> (side-effect: collects & logs).
	 *
	 * **Example:**
	 * Input: Flow("foo","bar")
	 * Output: (logs each item in a suspending context)
	 */
	@Bean
	fun suspendConsumerFlow(): suspend (Flow<String>) -> Unit = { flowInput ->
		flowInput.collect { item ->
			println("Flow item: $item")
		}
	}
}
