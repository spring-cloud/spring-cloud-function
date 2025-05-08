
package org.springframework.cloud.function.kotlin.wrapper

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.javaType
import kotlin.reflect.typeOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Flux
import kotlin.Unit
import org.springframework.cloud.function.context.wrapper.KotlinConsumerFlowWrapper

/*
 * @author Adrien Poupard
 */
@OptIn(ExperimentalStdlibApi::class)
class KotlinConsumerFlowWrapperTest {

    // Sample consumer function that accepts a Flow
    private val collectedItems = mutableListOf<String>()

    private val sampleConsumer: (Flow<String>) -> Unit = { flow ->
        runBlocking {
            collectedItems.clear()
            flow.collect { collectedItems.add(it) }
        }
    }

    @Test
    fun `test isValid with valid consumer flow type`() {
        // Given
        val functionType = typeOf<(Flow<String>) -> Unit>().javaType
        val types = arrayOf(typeOf<Flow<String>>().javaType, typeOf<Unit>().javaType)

        // When
        val result = KotlinConsumerFlowWrapper.isValid(functionType, types)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `test isValid with invalid consumer type`() {
        // Given
        val functionType = typeOf<(String) -> Unit>().javaType
        val types = arrayOf(typeOf<String>().javaType, typeOf<Unit>().javaType)

        // When
        val result = KotlinConsumerFlowWrapper.isValid(functionType, types)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `test asRegistrationFunction creates wrapper correctly`() {
        // Given
        val functionName = "testFunction"
        val types = arrayOf(typeOf<Flow<String>>().javaType, typeOf<Unit>().javaType)

        // When
        val wrapper = KotlinConsumerFlowWrapper.asRegistrationFunction(functionName, sampleConsumer, types)

        // Then
        assertThat(wrapper).isNotNull
        assertThat(wrapper.getName()).isEqualTo(functionName)
        assertThat(wrapper.resolvableType).isNotNull
    }

    @Test
    fun `test accept method processes Flux correctly`() {
        // Given
        val functionName = "testFunction"
        val types = arrayOf(typeOf<Flow<String>>().javaType, typeOf<Unit>().javaType)
        val wrapper = KotlinConsumerFlowWrapper.asRegistrationFunction(functionName, sampleConsumer, types)
        val inputFlux = Flux.just("test1", "test2", "test3") as Flux<Any>

        // When
        wrapper.accept(inputFlux)

        // Then
        assertThat(collectedItems).containsExactly("test1", "test2", "test3")
    }

    @Test
    fun `test invoke method processes Flux correctly`() {
        // Given
        val functionName = "testFunction"
        val types = arrayOf(typeOf<Flow<String>>().javaType, typeOf<Unit>().javaType)
        val wrapper = KotlinConsumerFlowWrapper.asRegistrationFunction(functionName, sampleConsumer, types)
        val inputFlux = Flux.just("test4", "test5", "test6") as Flux<Any>

        // When
        wrapper.accept(inputFlux)

        // Then
        assertThat(collectedItems).containsExactly("test4", "test5", "test6")
    }
}
