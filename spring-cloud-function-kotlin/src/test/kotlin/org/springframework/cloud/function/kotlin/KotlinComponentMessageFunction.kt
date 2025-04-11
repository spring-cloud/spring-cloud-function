package org.springframework.cloud.function.kotlin

import org.springframework.messaging.Message
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component

import java.util.function.Function

@Component
class KotlinComponentMessageFunction : (List<Message<Char>>) -> List<Message<Char>> {
	override fun invoke(input: List<Message<Char>>): List<Message<Char>> {
		return input
	}
}
