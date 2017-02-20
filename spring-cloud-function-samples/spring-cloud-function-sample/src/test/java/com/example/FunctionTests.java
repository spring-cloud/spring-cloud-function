/*
 * Copyright 2017 the original author or authors.
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

import org.junit.Test;

import com.example.functions.CharCounter;
import com.example.functions.Exclaimer;
import com.example.functions.Greeter;

import reactor.core.publisher.Flux;

public class FunctionTests {

	private final SampleApplication functions = new SampleApplication();

	@Test
	public void testUppercase() {
		String output = functions.uppercase().apply("foobar");
		assertEquals("FOOBAR", output);
	}

	@Test
	public void testLowercase() {
		Flux<String> output = functions.lowercase().apply(Flux.just("FOO", "BAR"));
		List<String> results = output.collectList().block();
		assertEquals(2, results.size());
		assertEquals("foo", results.get(0));
		assertEquals("bar", results.get(1));
	}

	@Test
	public void testHello() {
		String output = functions.hello().get();
		assertEquals("hello", output);	
	}

	@Test
	public void testWords() {
		Flux<String> output = functions.words().get();
		List<String> results = output.collectList().block();
		assertEquals(2, results.size());
		assertEquals("foo", results.get(0));
		assertEquals("bar", results.get(1));
	}

	@Test
	public void testGreeter() {
		assertEquals("Hello World", new Greeter().apply("World"));
	}

	@Test
	public void testExclaimer() {
		Flux<String> input = Flux.just("foo", "bar");
		Flux<String> output = new Exclaimer().apply(input);
		List<String> results = output.collectList().block();
		assertEquals(2, results.size());
		assertEquals("foo!!!", results.get(0));
		assertEquals("bar!!!", results.get(1));
	}

	@Test
	public void testCharCounter() {
		assertEquals((Integer) 21, new CharCounter().apply("this is 21 chars long"));
	}
}
