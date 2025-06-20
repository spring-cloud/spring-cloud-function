
package org.springframework.cloud.function.kotlin.wrapper

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Type
import kotlin.reflect.javaType
import kotlin.reflect.typeOf
import org.springframework.core.ResolvableType
import kotlin.Unit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Flux
import org.springframework.cloud.function.context.wrapper.KotlinConsumerSuspendFlowWrapper

/*
 * @author Adrien Poupard
 */
@OptIn(ExperimentalStdlibApi::class)
class KotlinConsumerSuspendFlowWrapperTest {

    // Sample suspend consumer function that accepts Flow
    private var lastConsumedValues = mutableListOf<String>()

    private val sampleSuspendFlowConsumer: suspend (Flow<String>) -> Unit = { flow ->
        flow.collect { value ->
            lastConsumedValues.add(value)
        }
    }

    @Test
    fun `test isValid with valid suspend flow consumer type`() {
        // Given
        val functionType = sampleSuspendFlowConsumer.javaClass.genericInterfaces[0]
        val types = arrayOf(
            typeOf<Flow<String>>().javaType,
            typeOf<kotlin.coroutines.Continuation<Unit>>().javaType,
            typeOf<Unit>().javaType
        )

        // When
        val result = KotlinConsumerSuspendFlowWrapper.isValid(functionType, types)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `test isValid with invalid consumer type (not flow)`() {
        // Given
        // Create a sample suspend consumer that doesn't use Flow
        val sampleNonFlowConsumer: suspend (String) -> Unit = { _ -> }
        val functionType = sampleNonFlowConsumer.javaClass.genericInterfaces[0]
        val types = arrayOf(
            typeOf<String>().javaType,
            typeOf<kotlin.coroutines.Continuation<Unit>>().javaType,
            typeOf<Unit>().javaType
        )

        // When
        val result = KotlinConsumerSuspendFlowWrapper.isValid(functionType, types)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `test asRegistrationFunction creates wrapper correctly`() {
        // Given
        val functionName = "testFunction"
        val types = arrayOf(
            typeOf<Flow<String>>().javaType,
            typeOf<kotlin.coroutines.Continuation<Unit>>().javaType,
            typeOf<Unit>().javaType
        )

        // When
        val wrapper = KotlinConsumerSuspendFlowWrapper.asRegistrationFunction(functionName, sampleSuspendFlowConsumer, types)

        // Then
        assertThat(wrapper).isNotNull
        assertThat(wrapper.getName()).isEqualTo(functionName)
        assertThat(wrapper.getResolvableType()).isNotNull
    }

    @Test
    fun `test accept method processes flow input correctly`() {
        // Given
        val functionName = "testFunction"
        val types = arrayOf(
            typeOf<Flow<String>>().javaType,
            typeOf<kotlin.coroutines.Continuation<Unit>>().javaType,
            typeOf<Unit>().javaType
        )
        val wrapper = KotlinConsumerSuspendFlowWrapper.asRegistrationFunction(functionName, sampleSuspendFlowConsumer, types)
        val input = Flux.just("test1", "test2", "test3") as Flux<Any>
        lastConsumedValues.clear()

        // When
        wrapper.accept(input)

        // Wait a bit for the async operation to complete
        Thread.sleep(100)

        // Then
        assertThat(lastConsumedValues).contains("test1", "test2", "test3")
    }

    @Test
    fun `test constructor with type parameter`() {
        // Given
        val functionName = "testFunction"
        val type = ResolvableType.forClassWithGenerics(
            java.util.function.Consumer::class.java,
            ResolvableType.forClassWithGenerics(
                reactor.core.publisher.Flux::class.java,
                ResolvableType.forClass(String::class.java)
            )
        )

        // When
        val wrapper = KotlinConsumerSuspendFlowWrapper(sampleSuspendFlowConsumer, type, functionName)

        // Then
        assertThat(wrapper).isNotNull
        assertThat(wrapper.getName()).isEqualTo(functionName)
        assertThat(wrapper.getResolvableType()).isEqualTo(type)
    }
}
