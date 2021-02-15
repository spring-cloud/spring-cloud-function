package com.example.kotlin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.util.function.Function
import org.springframework.context.annotation.Configuration

@Configuration
class DemoKotlinConfiguration {
	@Bean
	fun uppercase(): (String) -> String {
		return { it.toUpperCase() }
	}
}



