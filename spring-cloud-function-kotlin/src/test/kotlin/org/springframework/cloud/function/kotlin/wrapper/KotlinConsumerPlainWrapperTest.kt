
package org.springframework.cloud.function.kotlin.wrapper

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Type
import kotlin.reflect.javaType
import kotlin.reflect.typeOf
import kotlin.Unit
import org.springframework.cloud.function.context.wrapper.KotlinConsumerPlainWrapper

/*
 * @author Adrien Poupard
 */
@OptIn(ExperimentalStdlibApi::class)
class KotlinConsumerPlainWrapperTest {

    // Sample consumer function that accepts a plain object
    private var lastConsumedValue: String? = null

    private val sampleConsumer: (String) -> Unit = { input ->
        lastConsumedValue = input
    }

    @Test
    fun `test isValid with valid consumer plain type`() {
        // Given
        val functionType = typeOf<(String) -> Unit>().javaType
        val types = arrayOf(typeOf<String>().javaType, typeOf<Unit>().javaType)

        // When
        val result = KotlinConsumerPlainWrapper.isValid(functionType, types)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `test isValid with invalid consumer type (flow)`() {
        // Given
        val functionType = typeOf<(kotlinx.coroutines.flow.Flow<String>) -> Unit>().javaType
        val types = arrayOf(typeOf<kotlinx.coroutines.flow.Flow<String>>().javaType, typeOf<Unit>().javaType)

        // When
        val result = KotlinConsumerPlainWrapper.isValid(functionType, types)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `test asRegistrationFunction creates wrapper correctly`() {
        // Given
        val functionName = "testFunction"
        val types = arrayOf(typeOf<String>().javaType, typeOf<Unit>().javaType)

        // When
        val wrapper = KotlinConsumerPlainWrapper.asRegistrationFunction(functionName, sampleConsumer, types)

        // Then
        assertThat(wrapper).isNotNull
        assertThat(wrapper.getName()).isEqualTo(functionName)
        assertThat(wrapper.getResolvableType()).isNotNull
    }

    @Test
    fun `test accept method processes input correctly`() {
        // Given
        val functionName = "testFunction"
        val types = arrayOf(typeOf<String>().javaType, typeOf<Unit>().javaType)
        val wrapper = KotlinConsumerPlainWrapper.asRegistrationFunction(functionName, sampleConsumer, types)
        val input = "test input"

        // When
        wrapper.accept(input)

        // Then
        assertThat(lastConsumedValue).isEqualTo(input)
    }

    @Test
    fun `test accept method with Function1 implementation`() {
        // Given
        val functionName = "testFunction"
        val types = arrayOf(typeOf<String>().javaType, typeOf<Unit>().javaType)
        val wrapper = KotlinConsumerPlainWrapper.asRegistrationFunction(functionName, sampleConsumer, types)
        val input = "test with Function1"

        // When
        wrapper.accept(input)

        // Then
        assertThat(lastConsumedValue).isEqualTo(input)
    }
}
