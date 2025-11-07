package org.springframework.cloud.function.kotlin

import org.springframework.cloud.function.context.PostProcessingFunction
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import java.util.function.Function

@Component
class KotlinPostProcessingFunction : PostProcessingFunction<String, String> {

	var invoked = false

	override fun apply(t: String): String {
		return t.uppercase();
	}

	override fun postProcess(result: Message<String>?) {
		invoked = true;
	}
}
