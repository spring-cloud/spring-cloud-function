/*
 * Copyright 2012-2019 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import reactor.core.publisher.Flux;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.util.MultiValueMap;

// @checkstyle:off
@SpringBootApplication(proxyBeanMethods = false)
public class SampleApplication {

	public static void main(String[] args) {
		SpringApplication.run(SampleApplication.class, args);
	}

	@Bean
	public Function<Foo, Bar> uppercase() {
		return value -> new Bar(value.uppercase());
	}

	@Bean
	public Function<MultiValueMap<String, String>, Map<String, Integer>> sum() {
		return multiValueMap -> {
			Map<String, Integer> result = new HashMap<>();
			multiValueMap.forEach((s, strings) -> result.put(s,
				strings.stream().mapToInt(Integer::parseInt).sum()));
			return result;
		};
	}

	@Bean
	public Supplier<Flux<Foo>> words() {
		return () -> Flux.fromArray(new Foo[] {new Foo("foo"), new Foo("bar")}).log();
	}

}
// @checkstyle:on

class Foo {

	private String value;

	Foo() {
	}

	Foo(String value) {
		this.value = value;
	}

	public String lowercase() {
		return this.value.toLowerCase();
	}

	public String uppercase() {
		return this.value.toUpperCase();
	}

	public String getValue() {
		return this.value;
	}

	public void setValue(String value) {
		this.value = value;
	}

}

class Bar {

	private String value;

	Bar() {
	}

	Bar(String value) {
		this.value = value;
	}

	public String getValue() {
		return this.value;
	}

	public void setValue(String value) {
		this.value = value;
	}

}
