package org.springframework.cloud.function.kotlin

import org.springframework.stereotype.Component

@Component
class ComponentUppercase : (String) -> String {
	override fun invoke(p1: String): String {
		return p1.uppercase()
	}
}
