

package org.springframework.cloud.function.kotlin.wrapper

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.javaType
import kotlin.reflect.typeOf
import org.springframework.cloud.function.context.wrapper.KotlinSupplierPlainWrapper
import org.springframework.core.ResolvableType
import java.util.function.Supplier

/**
 * @author Adrien Poupard
 */
@OptIn(ExperimentalStdlibApi::class)
class KotlinSupplierPlainWrapperTest {

    // Sample supplier function that returns a plain object
    private val sampleSupplier: () -> String = {
        "Hello from supplier"
    }

    @Test
    fun `test isValid with valid supplier plain type`() {
        // Given
        val functionType = typeOf<() -> String>().javaType
        val types = arrayOf(typeOf<String>().javaType)

        // When
        val result = KotlinSupplierPlainWrapper.isValid(functionType, types)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `test isValid with invalid supplier type (not a supplier)`() {
        // Given
        val functionType = typeOf<(String) -> String>().javaType
        val types = arrayOf(typeOf<String>().javaType, typeOf<String>().javaType)

        // When
        val result = KotlinSupplierPlainWrapper.isValid(functionType, types)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `test asRegistrationFunction creates wrapper correctly`() {
        // Given
        val functionName = "testSupplier"
        val types = arrayOf(typeOf<String>().javaType)

        // When
        val wrapper = KotlinSupplierPlainWrapper.asRegistrationFunction(functionName, sampleSupplier, types)

        // Then
        assertThat(wrapper.getName()).isEqualTo(functionName)
		assertThat(wrapper.getResolvableType()).isNotNull
    }

    @Test
    fun `test get method returns correct value`() {
        // Given
        val functionName = "testSupplier"
        val types = arrayOf(typeOf<String>().javaType)
        val wrapper = KotlinSupplierPlainWrapper.asRegistrationFunction(functionName, sampleSupplier, types)

        // When
        val result = wrapper.get()

        // Then
        assertThat(result).isEqualTo("Hello from supplier")
    }

    @Test
    fun `test apply method with empty input returns supplier result`() {
        // Given
        val functionName = "testSupplier"
        val types = arrayOf(typeOf<String>().javaType)
        val wrapper = KotlinSupplierPlainWrapper.asRegistrationFunction(functionName, sampleSupplier, types)

        // When
        val result = wrapper.apply(null)

        // Then
        assertThat(result).isEqualTo("Hello from supplier")
    }

    @Test
    fun `test constructor with type parameter`() {
        // Given
        val functionName = "testSupplier"
        val type = ResolvableType.forClassWithGenerics(
            Supplier::class.java,
            ResolvableType.forClass(String::class.java)
        )

        // When
        val wrapper = KotlinSupplierPlainWrapper(
            sampleSupplier,
            type,
            functionName
        )

        // Then
        assertThat(wrapper.getName()).isEqualTo(functionName)
        assertThat(wrapper.getResolvableType()).isEqualTo(type)
        assertThat(wrapper.get()).isEqualTo("Hello from supplier")
    }
}
