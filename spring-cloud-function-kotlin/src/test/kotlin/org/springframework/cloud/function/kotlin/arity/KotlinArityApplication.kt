package org.springframework.cloud.function.kotlin.arity

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
open class KotlinArityApplication

fun main(args: Array<String>) {
	SpringApplication.run(KotlinArityApplication::class.java, *args)
}
