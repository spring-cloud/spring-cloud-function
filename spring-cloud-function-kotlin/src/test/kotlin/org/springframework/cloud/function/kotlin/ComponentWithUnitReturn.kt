package org.springframework.cloud.function.kotlin

import org.springframework.messaging.Message
import org.springframework.stereotype.Component

@Component
class ComponentWithUnitReturn() : (Message<String>) -> Unit {
	override fun invoke(message: Message<String>) {
		println(message.payload)
	}
}
