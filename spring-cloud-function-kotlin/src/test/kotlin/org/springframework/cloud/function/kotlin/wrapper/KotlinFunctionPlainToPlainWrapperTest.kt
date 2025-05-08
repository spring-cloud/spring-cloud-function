package org.springframework.cloud.function.kotlin.wrapper

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Type
import kotlin.reflect.javaType
import kotlin.reflect.typeOf
import org.springframework.cloud.function.context.wrapper.KotlinFunctionPlainToPlainWrapper
import org.springframework.core.ResolvableType

/**
 * @author Adrien Poupard
 */
@OptIn(ExperimentalStdlibApi::class)
class KotlinFunctionPlainToPlainWrapperTest {

    // Sample function that transforms a String to an Int
    private val sampleFunction: (String) -> Int = { input ->
        input.length
    }

    @Test
    fun `test isValid with valid object to object function type`() {
        // Given
        val functionType = typeOf<(String) -> Int>().javaType
        val types = arrayOf(
            typeOf<String>().javaType,
            typeOf<Int>().javaType
        )

        // When
        val result = KotlinFunctionPlainToPlainWrapper.isValid(functionType, types)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `test asRegistrationFunction creates wrapper correctly`() {
        // Given
        val functionName = "testFunction"
        val types = arrayOf(
            typeOf<String>().javaType,
            typeOf<Int>().javaType
        )

        // When
        val wrapper = KotlinFunctionPlainToPlainWrapper.asRegistrationFunction(
            functionName, 
            sampleFunction, 
            types
        )

        // Then
        assertThat(wrapper).isNotNull
        assertThat(wrapper.getName()).isEqualTo(functionName)
        assertThat(wrapper.getResolvableType()).isNotNull
    }

    @Test
    fun `test apply method processes input correctly`() {
        // Given
        val functionName = "testFunction"
        val types = arrayOf(
            typeOf<String>().javaType,
            typeOf<Int>().javaType
        )
        val wrapper = KotlinFunctionPlainToPlainWrapper.asRegistrationFunction(
            functionName, 
            sampleFunction, 
            types
        )
        val input = "test input"

        // When
        val result = wrapper.apply(input)

        // Then
        assertThat(result).isEqualTo(10) // "test input".length = 10
    }

    @Test
    fun `test invoke method processes input correctly`() {
        // Given
        val functionName = "testFunction"
        val types = arrayOf(
            typeOf<String>().javaType,
            typeOf<Int>().javaType
        )
        val wrapper = KotlinFunctionPlainToPlainWrapper.asRegistrationFunction(
            functionName, 
            sampleFunction, 
            types
        )
        val input = "another test"

        // When
        val result = wrapper.invoke(input)

        // Then
        assertThat(result).isEqualTo(12) // "another test".length = 12
    }

    @Test
    fun `test constructor with type parameter`() {
        // Given
        val functionName = "testFunction"
        val type = ResolvableType.forClassWithGenerics(
            java.util.function.Function::class.java,
            ResolvableType.forClass(String::class.java),
            ResolvableType.forClass(Int::class.java)
        )
        // When
        val wrapper =
			KotlinFunctionPlainToPlainWrapper(
				sampleFunction,
				type,
				functionName
			)

        // Then
        assertThat(wrapper).isNotNull
        assertThat(wrapper.getName()).isEqualTo(functionName)
        assertThat(wrapper.getResolvableType()).isEqualTo(type)
    }
}
