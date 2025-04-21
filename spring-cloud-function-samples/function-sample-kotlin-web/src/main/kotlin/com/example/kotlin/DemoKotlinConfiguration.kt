package com.example.kotlin

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DemoKotlinConfiguration {
	@Bean
	fun uppercase(): (String) -> String {
		return { it.uppercase() }
	}
}



