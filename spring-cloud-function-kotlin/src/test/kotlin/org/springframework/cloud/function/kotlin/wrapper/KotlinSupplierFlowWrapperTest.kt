

package org.springframework.cloud.function.kotlin.wrapper

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.javaType
import kotlin.reflect.typeOf
import org.springframework.cloud.function.context.wrapper.KotlinSupplierFlowWrapper
import org.springframework.core.ResolvableType
import reactor.core.publisher.Flux
import java.util.function.Supplier

/**
 * @author Adrien Poupard
 */
@OptIn(ExperimentalStdlibApi::class)
class KotlinSupplierFlowWrapperTest {

    // Sample supplier function that returns a Flow
    private val sampleSupplier: () -> Flow<String> = {
        flow {
            emit("Hello")
            emit("from")
            emit("flow")
            emit("supplier")
        }
    }

    @Test
    fun `test isValid with valid supplier flow type`() {
        // Given
        val functionType = typeOf<() -> Flow<String>>().javaType
        val types = arrayOf(typeOf<Flow<String>>().javaType)

        // When
        val result = KotlinSupplierFlowWrapper.isValid(functionType, types)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `test isValid with invalid supplier type (not a flow)`() {
        // Given
        val functionType = typeOf<() -> String>().javaType
        val types = arrayOf(typeOf<String>().javaType)

        // When
        val result = KotlinSupplierFlowWrapper.isValid(functionType, types)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `test asRegistrationFunction creates wrapper correctly`() {
        // Given
        val functionName = "testFlowSupplier"
        val types = arrayOf(typeOf<Flow<String>>().javaType)

        // When
        val wrapper = KotlinSupplierFlowWrapper.asRegistrationFunction(functionName, sampleSupplier, types)

        // Then
        assertThat(wrapper != null).isTrue()
        assertThat(wrapper.getName()).isEqualTo(functionName)
        assertThat(wrapper.getResolvableType() != null).isTrue()
    }

    @Test
    fun `test get method returns correct Flux`() {
        // Given
        val functionName = "testFlowSupplier"
        val types = arrayOf(typeOf<Flow<String>>().javaType)
        val wrapper = KotlinSupplierFlowWrapper.asRegistrationFunction(functionName, sampleSupplier, types)

        // When
        val result = wrapper.get()

        // Then
        assertThat(result).isInstanceOf(Flux::class.java)
        val items = result.collectList().block()
        assertThat(items).containsExactly("Hello", "from", "flow", "supplier")
    }

    @Test
    fun `test invoke method returns correct Flow`() {
        // Given
        val functionName = "testFlowSupplier"
        val types = arrayOf(typeOf<Flow<String>>().javaType)
        val wrapper = KotlinSupplierFlowWrapper.asRegistrationFunction(functionName, sampleSupplier, types)

        // When
        val result = wrapper.invoke()

        // Then
        assertThat(result).isNotNull
        // Collect items from Flow
        val items = runBlocking {
            result.toList()
        }
        assertThat(items).containsExactly("Hello", "from", "flow", "supplier")
    }

    @Test
    fun `test constructor with type parameter`() {
        // Given
        val functionName = "testFlowSupplier"
        val fluxType = ResolvableType.forClassWithGenerics(
            Flux::class.java,
            ResolvableType.forClass(String::class.java)
        )
        val type = ResolvableType.forClassWithGenerics(
            Supplier::class.java,
            fluxType
        )

        // When
        val wrapper = KotlinSupplierFlowWrapper(
            sampleSupplier,
            type,
            functionName
        )

        // Then
        assertThat(wrapper != null).isTrue()
        assertThat(wrapper.getName()).isEqualTo(functionName)
        assertThat(wrapper.getResolvableType()).isEqualTo(type)

        // Test get method
        val flux = wrapper.get()
        val items = flux.collectList().block()
        assertThat(items).containsExactly("Hello", "from", "flow", "supplier")
    }
}
