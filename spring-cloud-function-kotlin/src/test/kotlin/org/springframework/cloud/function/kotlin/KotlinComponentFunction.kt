package org.springframework.cloud.function.kotlin

import org.springframework.stereotype.Component
import java.util.function.Function

@Component
class KotlinComponentFunction : Function<String, String> {

	override fun apply(t: String): String {
		return t.uppercase();
	}
}
