/*
 * Copyright 2013-2016 the original author or authors.
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
package com.example;

import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import reactor.core.publisher.Flux;

@SpringBootApplication
public class SampleApplication {

	@Bean
	public Function<Flux<String>, Flux<String>> uppercase() {
		return flux -> flux.map(value -> value.toUpperCase());
	}

	@Bean
	public Supplier<Flux<String>> words() {
		return () -> Flux.fromArray(new String[] { "foo", "bar" });
	}

	@Bean
	public Function<Flux<String>, Flux<String>> lowercase() {
		return flux -> flux.map(value -> value.toLowerCase());
	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(SampleApplication.class, args);
	}

}
