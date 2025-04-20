package com.example.kotlin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
open class DemoKotlinApplication 

fun main(args: Array<String>) {
	runApplication<DemoKotlinApplication>(*args)
}
