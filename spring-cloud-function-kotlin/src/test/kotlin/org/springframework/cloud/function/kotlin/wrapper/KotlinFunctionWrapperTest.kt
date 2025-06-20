package org.springframework.cloud.function.kotlin.wrapper

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.cloud.function.context.wrapper.KotlinFunctionWrapper
import org.springframework.core.ResolvableType

/*
 * @author Adrien Poupard
 */
class KotlinFunctionWrapperTest {

    // Simple implementation of KotlinFunctionWrapper for testing
    private class TestKotlinFunctionWrapper(
        private val type: ResolvableType,
        private val name: String
    ) : KotlinFunctionWrapper {
        override fun getResolvableType(): ResolvableType = type
        override fun getName(): String = name
    }

    @Test
    fun `test getName returns correct name`() {
        // Given
        val expectedName = "testFunction"
        val type = ResolvableType.forClass(String::class.java)
        val wrapper = TestKotlinFunctionWrapper(type, expectedName)

        // When
        val actualName = wrapper.getName()

        // Then
        assertThat(actualName).isEqualTo(expectedName)
    }

    @Test
    fun `test getResolvableType returns correct type`() {
        // Given
        val name = "testFunction"
        val expectedType = ResolvableType.forClass(String::class.java)
        val wrapper = TestKotlinFunctionWrapper(expectedType, name)

        // When
        val actualType = wrapper.getResolvableType()

        // Then
        assertThat(actualType).isEqualTo(expectedType)
    }

    @Test
    fun `test implementation with complex type`() {
        // Given
        val name = "complexFunction"
        val expectedType = ResolvableType.forClassWithGenerics(
            java.util.function.Function::class.java,
            ResolvableType.forClass(String::class.java),
            ResolvableType.forClassWithGenerics(
                reactor.core.publisher.Flux::class.java,
                ResolvableType.forClass(Int::class.java)
            )
        )
        val wrapper = TestKotlinFunctionWrapper(expectedType, name)

        // When
        val actualType = wrapper.getResolvableType()
        val actualName = wrapper.getName()

        // Then
        assertThat(actualType).isEqualTo(expectedType)
        assertThat(actualName).isEqualTo(name)
    }
}
