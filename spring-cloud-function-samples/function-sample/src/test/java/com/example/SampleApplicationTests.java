/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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

import static org.junit.Assert.assertEquals;

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
		assertEquals("FOOBAR", output);
	}

	@Test
	public void testLowercase() {
		Flux<String> output = this.lowercase.apply(Flux.just("FOO", "BAR"));
		List<String> results = output.collectList().block();
		assertEquals(2, results.size());
		assertEquals("foo", results.get(0));
		assertEquals("bar", results.get(1));
	}

	@Test
	public void testHello() {
		String output = this.hello.get();
		assertEquals("hello", output);
	}

	// the following are contributed via @FunctionScan:

	@Test
	public void testWords() {
		Flux<String> output = this.words.get();
		List<String> results = output.collectList().block();
		assertEquals(2, results.size());
		assertEquals("foo", results.get(0));
		assertEquals("bar", results.get(1));
	}

	@Test
	public void testCompiledUppercase() {
		String output = this.compiledUppercase.apply("foobar");
		assertEquals("FOOBAR", output);
	}

	@Test
	public void testCompiledLowercase() {
		Flux<String> input = Flux.just("FOO", "BAR");
		Flux<String> output = this.compiledLowercase.apply(input);
		List<String> results = output.collectList().block();
		assertEquals(2, results.size());
		assertEquals("foo", results.get(0));
		assertEquals("bar", results.get(1));
	}

	@Test
	public void testGreeter() {
		String greeting = this.greeter.apply("World");
		assertEquals("Hello World", greeting);
	}

	@Test
	public void testExclaimer() {
		Flux<String> input = Flux.just("foo", "bar");
		Flux<String> output = this.exclaimer.apply(input);
		List<String> results = output.collectList().block();
		assertEquals(2, results.size());
		assertEquals("foo!!!", results.get(0));
		assertEquals("bar!!!", results.get(1));
	}

	@Test
	public void testCharCounter() {
		Integer length = this.charCounter.apply("the quick brown fox");
		assertEquals(new Integer(19), length);
	}

}
