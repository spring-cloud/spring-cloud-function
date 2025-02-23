package org.springframework.cloud.function.context.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Type
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaField

enum class  FunctionType {
	Function, SuspendFunction,
	Consumer, SuspendConsumer,
	Supplier, SuspendSupplier,
}

/**
 * ## List of Combinations Tested (in requested order):
 *  1. (T) -> R                     -> functionSingleToSingle
 *  2. (T) -> Flow<R>               -> functionSingleToFlow
 *  3. (Flow<T>) -> R               -> functionFlowToSingle
 *  4. (Flow<T>) -> Flow<R>         -> functionFlowToFlow
 *  5. suspend (T) -> R             -> functionSuspendSingleToSingle
 *  6. suspend (T) -> Flow<R>       -> functionSuspendSingleToFlow
 *  7. suspend (Flow<T>) -> R       -> functionSuspendFlowToSingle
 *  8. suspend (Flow<T>) -> Flow<R> -> functionSuspendFlowToFlow
 *  9. () -> R                      -> supplierSingle
 *  10. () -> Flow<R>               -> supplierFlow
 *  11. suspend () -> R             -> supplierSuspendSingle
 *  12. suspend () -> Flow<R>       -> supplierSuspendFlow
 *  13. (T) -> Unit                 -> consumerSingle
 *  14. (Flow<T>) -> Unit           -> consumerFlow
 *  15. suspend (T) -> Unit         -> consumerSuspendSingle
 *  16. suspend (Flow<T>) -> Unit   -> consumerSuspendFlow
 */
class KotlinTypeValidationTests {

	/* 1. (T) -> R -> functionSingleToSingle */
	@Test
	fun `test functionSingleToSingle`() {
		val (propertyName, type) = Sample.Function::singleToSingle.propertyType()
		val paramTypes = arrayOf<Type>(
			Sample.Type.stringType,
			Sample.Type.intType
		)
		val isValid = isValidKotlinFunction(type, paramTypes)

		assertThat(isValid)
			.describedAs("`$propertyName` should be recognized as (T)->R function.")
			.isTrue()
	}

	/* 2. (T) -> Flow<R> -> functionSingleToFlow */
	@Test
	fun `test functionSingleToFlow`() {
		val (propertyName, type) = Sample.Function::singleToFlow.propertyType()
		val paramTypes = arrayOf<Type>(
			Sample.Type.stringType,
			Sample.Type.flowStringType
		)
		val isValid = isValidKotlinFunction(type, paramTypes)

		assertThat(isValid)
			.describedAs("`$propertyName` should be (T)->Flow<R>.")
			.isTrue()
	}

	/* 3. (Flow<T>) -> R -> functionFlowToSingle */
	@Test
	fun `test functionFlowToSingle`() {
		val (propertyName, type) = Sample.Function::flowToSingle.propertyType()
		val paramTypes = arrayOf<Type>(
			Sample.Type.flowStringType,
			Sample.Type.intType
		)
		val isValid = isValidKotlinFunction(type, paramTypes)

		assertThat(isValid)
			.describedAs("`$propertyName` should be (Flow<T>)->R.")
			.isTrue()
	}

	/* 4. (Flow<T>) -> Flow<R> -> functionFlowToFlow */
	@Test
	fun `test functionFlowToFlow`() {
		val (propertyName, type) = Sample.Function::flowToFlow.propertyType()
		val paramTypes = arrayOf<Type>(
			Sample.Type.flowIntType,
			Sample.Type.flowStringType
		)
		val isValid = isValidKotlinFunction(type, paramTypes)

		assertThat(isValid)
			.describedAs("`$propertyName` should be (Flow<T>)->Flow<R>.")
			.isTrue()
	}

	/* 5. suspend (T) -> R -> functionSuspendSingleToSingle */
	@Test
	fun `test functionSuspendSingleToSingle`() {
		val (propertyName, type) = Sample.Function::suspendSingleToSingle.propertyType()
		val paramTypes = arrayOf<Type>(
			Sample.Type.stringType,
			Sample.Type.continuationIntType,
			Sample.Type.intType
		)
		val isValid = isValidKotlinSuspendFunction(type, paramTypes)

		assertThat(isValid)
			.describedAs("`$propertyName` should be suspend (T)->R.")
			.isTrue()
	}

	/* 6. suspend (T) -> Flow<R> -> functionSuspendSingleToFlow */
	@Test
	fun `test functionSuspendSingleToFlow`() {
		val (propertyName, type) = Sample.Function::suspendSingleToFlow.propertyType()
		val paramTypes = arrayOf<Type>(
			Sample.Type.stringType,
			Sample.Type.continuationFlowStringType,
			Sample.Type.flowStringType
		)
		val isValid = isValidKotlinSuspendFunction(type, paramTypes)

		assertThat(isValid)
			.describedAs("`$propertyName` should be suspend (T)->Flow<R>.")
			.isTrue()
	}

	/* 7. suspend (Flow<T>) -> R -> functionSuspendFlowToSingle */
	@Test
	fun `test functionSuspendFlowToSingle`() {
		val (propertyName, type) = Sample.Function::suspendFlowToSingle.propertyType()
		val paramTypes = arrayOf<Type>(
			Sample.Type.flowStringType,
			Sample.Type.continuationIntType,
			Sample.Type.intType
		)
		val isValid = isValidKotlinSuspendFunction(type, paramTypes)

		assertThat(isValid)
			.describedAs("`$propertyName` should be suspend (Flow<T>)->R.")
			.isTrue()
	}

	/* 8. suspend (Flow<T>) -> Flow<R> -> functionSuspendFlowToFlow */
	@Test
	fun `test functionSuspendFlowToFlow`() {
		val (propertyName, type) = Sample.Function::suspendFlowToFlow.propertyType()
		val paramTypes = arrayOf<Type>(
			Sample.Type.flowStringType,
			Sample.Type.continuationFlowStringType,
			Sample.Type.flowStringType
		)
		val isValid = isValidKotlinSuspendFunction(type, paramTypes)

		assertThat(isValid)
			.describedAs("`$propertyName` should be suspend (Flow<T>)->Flow<R>.")
			.isTrue()
	}

	/* 9. () -> R -> supplierSingle */
	@Test
	fun `test supplierSingle`() {
		val (propertyName, type) = Sample.Supplier::unitToSingle.propertyType()

		val isValid = isValidKotlinSupplier(type)

		assertThat(isValid)
			.describedAs("`$propertyName` should be recognized as ()->R supplier.")
			.isTrue()
	}

	/* 10. () -> Flow<R> -> supplierFlow */
	@Test
	fun `test supplierFlow`() {
		val (propertyName, type) = Sample.Supplier::unitToFlow.propertyType()
		val paramTypes = arrayOf<Type>(
			Sample.Type.flowStringType
		)
		val isValid = isValidKotlinSupplier(type)

		assertThat(isValid)
			.describedAs("`$propertyName` should be recognized as ()->Flow<R> supplier.")
			.isTrue()
	}

	/* 11. suspend () -> R -> supplierSuspendSingle */
	@Test
	fun `test supplierSuspendSingle`() {
		val (propertyName, type) = Sample.Supplier::suspendUnitToSingle.propertyType()
		val paramTypes = arrayOf<Type>(
			Sample.Type.continuationStringType,
			Sample.Type.stringType
		)
		val isValid = isValidKotlinSuspendSupplier(type, paramTypes)

		assertThat(isValid)
			.describedAs("`$propertyName` should be recognized as suspend ()->R supplier.")
			.isTrue()
	}

	/* 12. suspend () -> Flow<R> -> supplierSuspendFlow */
	@Test
	fun `test supplierSuspendFlow`() {
		val (propertyName, type) = Sample.Supplier::suspendUnitToFlow.propertyType()
		val paramTypes = arrayOf<Type>(
			Sample.Type.continuationFlowStringType,
			Sample.Type.flowStringType
		)
		val isValid = isValidKotlinSuspendSupplier(type, paramTypes)

		assertThat(isValid)
			.describedAs("`$propertyName` should be recognized as suspend ()->Flow<R> supplier.")
			.isTrue()
	}

	/* 13. (T) -> Unit -> consumerSingle */
	@Test
	fun `test consumerSingle`() {
		val (propertyName, type) = Sample.Consumer::singleToUnit.propertyType()
		val paramTypes = arrayOf<Type>(
			Sample.Type.stringType,
			Sample.Type.unitType
		)
		val isValid = isValidKotlinConsumer(type, paramTypes)

		assertThat(isValid)
			.describedAs("`$propertyName` should be (T)->Unit consumer.")
			.isTrue()
	}

	/* 14. (Flow<T>) -> Unit -> consumerFlow */
	@Test
	fun `test consumerFlow`() {
		val (propertyName, type) = Sample.Consumer::flowToUnit.propertyType()
		val paramTypes = arrayOf<Type>(
			Sample.Type.flowStringType,
			Sample.Type.unitType
		)
		val isValid = isValidKotlinConsumer(type, paramTypes)

		assertThat(isValid)
			.describedAs("`$propertyName` should be (Flow<T>)->Unit consumer.")
			.isTrue()
	}

	/* 15. suspend (T) -> Unit -> consumerSuspendSingle */
	@Test
	fun `test consumerSuspendSingle`() {
		val (propertyName, type) = Sample.Consumer::suspendSingleToUnit.propertyType()
		val paramTypes = arrayOf<Type>(
			Sample.Type.stringType,
			Sample.Type.continuationUnitType,
			Sample.Type.unitType
		)
		val isValid = isValidKotlinSuspendConsumer(type, paramTypes)

		assertThat(isValid)
			.describedAs("`$propertyName` should be suspend (T)->Unit consumer.")
			.isTrue()
	}

	/* 16. suspend (Flow<T>) -> Unit -> consumerSuspendFlow */
	@Test
	fun `test consumerSuspendFlow`() {
		val (propertyName, type) = Sample.Consumer::suspendFlowToUnit.propertyType()
		val paramTypes = arrayOf<Type>(
			Sample.Type.flowStringType,
			Sample.Type.continuationUnitType,
			Sample.Type.unitType
		)
		val isValid = isValidKotlinSuspendConsumer(type, paramTypes)

		assertThat(isValid)
			.describedAs("`$propertyName` should be suspend (Flow<T>)->Unit consumer.")
			.isTrue()
	}

	// -------------------------------------------------------------------
	// Helper: reflect on a property by name and get its Type
	// -------------------------------------------------------------------
	private fun <T> KProperty<T>.propertyType(): Pair<String, Type> {
		val name = this.name
		val field = this.javaField ?: error("No backing field for property $name")
		val actualType = field.genericType
		return name to actualType
	}
}
