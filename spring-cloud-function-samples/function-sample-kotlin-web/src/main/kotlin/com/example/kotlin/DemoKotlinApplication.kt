package com.example.kotlin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.util.function.Function
import org.springframework.cloud.function.context.FunctionCatalog
import kotlin.jvm.internal.Reflection
import kotlin.jvm.javaClass

@SpringBootApplication
open class DemoKotlinApplication 

fun main(args: Array<String>) {
	runApplication<DemoKotlinApplication>(*args)
}
