/*
 * Copyright 2020-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.kotlin

import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.Function
import java.util.List

/**
 * @author Oleg Zhurakousky
 *
 */
@EnableAutoConfiguration
@Configuration
class KotlinLambdasConfiguration {
	@Bean
	fun kotlinFunction(): (String) -> String {
		return { it.toUpperCase() }
	}

	@Bean
	fun kotlinPojoFunction(): (Person) -> String {
		return { it.name.toString()}
	}
	
	@Bean
	fun kotlinListPojoFunction(): (List<Person>) -> String {
		return {
			"List of: " + it.get(0).name
		}
	}

	@Bean
	fun kotlinConsumer(): (String) -> Unit {
		return { println(it) }
	}

	@Bean
	fun kotlinSupplier(): () -> String {
		return { "Hello" }
	}

	@Bean
	fun javaFunction(): Function<String, String> {
		return Function { x -> x }
	}

}
