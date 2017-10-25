/*
 * Copyright 2016-2017 the original author or authors.
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

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import reactor.core.publisher.Flux;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SampleApplicationTests {

	@Test
	public void contextLoads() {
	}

	@Autowired
	private Function<String, String> uppercase;

	@Test
	public void testUppercase() {
		String output = this.uppercase.apply("foobar");
		assertEquals("FOOBAR", output);
	}

	@Autowired
	private Function<Flux<String>, Flux<String>> lowercase;

	@Test
	public void testLowercase() {
		Flux<String> output = this.lowercase.apply(Flux.just("FOO", "BAR"));
		List<String> results = output.collectList().block();
		assertEquals(2, results.size());
		assertEquals("foo", results.get(0));
		assertEquals("bar", results.get(1));		
	}

	@Autowired
	private Supplier<String> hello;

	@Test
	public void testHello() {
		String output = this.hello.get();
		assertEquals("hello", output);	
	}

	@Autowired
	private Supplier<Flux<String>> words;

	@Test
	public void testWords() {
		Flux<String> output = this.words.get();
		List<String> results = output.collectList().block();
		assertEquals(2, results.size());
		assertEquals("foo", results.get(0));
		assertEquals("bar", results.get(1));	
	}

	@Autowired
	private Function<String, String> compiledUppercase;

	@Test
	public void testCompiledUppercase() {
		String output = this.compiledUppercase.apply("foobar");
		assertEquals("FOOBAR", output);
	}

	@Autowired
	private Function<Flux<String>, Flux<String>> compiledLowercase;

	@Test
	public void testCompiledLowercase() {
		Flux<String> input = Flux.just("FOO", "BAR");
		Flux<String> output = this.compiledLowercase.apply(input);
		List<String> results = output.collectList().block();
		assertEquals(2, results.size());
		assertEquals("foo", results.get(0));
		assertEquals("bar", results.get(1));
	}

	// the following are contributed via @FunctionScan:

	@Autowired
	private Function<String, String> greeter;

	@Test
	public void testGreeter() {
		String greeting = this.greeter.apply("World");
		assertEquals("Hello World", greeting);
	}

	@Autowired
	private Function<Flux<String>, Flux<String>> exclaimer;

	@Test
	public void testExclaimer() {
		Flux<String> input = Flux.just("foo", "bar");
		Flux<String> output = this.exclaimer.apply(input);
		List<String> results = output.collectList().block();
		assertEquals(2, results.size());
		assertEquals("foo!!!", results.get(0));
		assertEquals("bar!!!", results.get(1));
	}

	@Autowired
	private Function<String, Integer> charCounter;

	@Test
	public void testCharCounter() {
		Integer length = this.charCounter.apply("the quick brown fox");
		assertEquals(new Integer(19), length);
	}
}