/*
 * Copyright 2021-2021 the original author or authors.
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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Flux
import java.util.function.Function

/**
 * @author Adrien Poupard
 *
 */
@EnableAutoConfiguration
@Configuration
open class KotlinSuspendFlowLambdasConfiguration {

	@Bean
	open fun kotlinFunction(): suspend (Flow<String>) -> Flow<String> =  { flow ->
		flow.map { value -> value.uppercase() }
	}
	
	@Bean
	open fun kotlinPojoFunction(): suspend (Flow<Person>) -> Flow<String> = { flow ->
		flow.map(Person::toString)
	}

	@Bean
	open fun kotlinConsumer(): suspend (Flow<String>) -> Unit = { flow ->
		flow.collect(::println)
	}

	@Bean
	open fun kotlinSupplier(): suspend () -> Flow<String>  = {
		flow {
			emit("Hello")
		}
	}

	@Bean
	open fun javaFunction(): Function<Flux<String>, Flux<String>> {
		return Function { x -> x }
	}

}
