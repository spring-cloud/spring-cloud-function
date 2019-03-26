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

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SampleApplicationTests {

	@Autowired
	private Function<String, String> uppercase;
	@Autowired
	private Function<Flux<String>, Flux<String>> lowercase;
	@Autowired
	private Supplier<String> hello;
	@Autowired
	private Supplier<Flux<String>> words;
	@Autowired
	private Function<String, String> compiledUppercase;
	@Autowired
	private Function<Flux<String>, Flux<String>> compiledLowercase;
	@Autowired
	private Function<String, String> greeter;
	@Autowired
	private Function<Flux<String>, Flux<String>> exclaimer;
	@Autowired
	private Function<String, Integer> charCounter;

	@Test
	public void contextLoads() {
	}

	@Test
	public void testUppercase() {
		String output = this.uppercase.apply("foobar");
		assertThat(output).isEqualTo("FOOBAR");
	}

	@Test
	public void testLowercase() {
		Flux<String> output = this.lowercase.apply(Flux.just("FOO", "BAR"));
		List<String> results = output.collectList().block();
		assertThat(results.size()).isEqualTo(2);
		assertThat(results.get(0)).isEqualTo("foo");
		assertThat(results.get(1)).isEqualTo("bar");
	}

	@Test
	public void testHello() {
		String output = this.hello.get();
		assertThat(output).isEqualTo("hello");
	}

	// the following are contributed via @FunctionScan:

	@Test
	public void testWords() {
		Flux<String> output = this.words.get();
		List<String> results = output.collectList().block();
		assertThat(results.size()).isEqualTo(2);
		assertThat(results.get(0)).isEqualTo("foo");
		assertThat(results.get(1)).isEqualTo("bar");
	}

	@Test
	public void testCompiledUppercase() {
		String output = this.compiledUppercase.apply("foobar");
		assertThat(output).isEqualTo("FOOBAR");
	}

	@Test
	public void testCompiledLowercase() {
		Flux<String> input = Flux.just("FOO", "BAR");
		Flux<String> output = this.compiledLowercase.apply(input);
		List<String> results = output.collectList().block();
		assertThat(results.size()).isEqualTo(2);
		assertThat(results.get(0)).isEqualTo("foo");
		assertThat(results.get(1)).isEqualTo("bar");
	}

	@Test
	public void testGreeter() {
		String greeting = this.greeter.apply("World");
		assertThat(greeting).isEqualTo("Hello World");
	}

	@Test
	public void testExclaimer() {
		Flux<String> input = Flux.just("foo", "bar");
		Flux<String> output = this.exclaimer.apply(input);
		List<String> results = output.collectList().block();
		assertThat(results.size()).isEqualTo(2);
		assertThat(results.get(0)).isEqualTo("foo!!!");
		assertThat(results.get(1)).isEqualTo("bar!!!");
	}

	@Test
	public void testCharCounter() {
		Integer length = this.charCounter.apply("the quick brown fox");
		assertThat(length).isEqualTo(new Integer(19));
	}

}
