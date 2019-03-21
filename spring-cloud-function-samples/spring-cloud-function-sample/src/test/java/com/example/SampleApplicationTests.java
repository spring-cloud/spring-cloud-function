/*
 * Copyright 2016 the original author or authors.
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

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import reactor.core.publisher.Flux;

public class SampleApplicationTests {

	private final SampleApplication functions = new SampleApplication();

	@Test
	public void testUppercase() {
		Flux<String> output = functions.uppercase().apply(Flux.just("foo", "bar"));
		List<String> results = output.collectList().block();
		assertEquals(2, results.size());
		assertEquals("FOO", results.get(0));
		assertEquals("BAR", results.get(1));
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
	public void testWords() {
		Flux<String> output = functions.words().get();
		List<String> results = output.collectList().block();
		assertEquals(2, results.size());
		assertEquals("foo", results.get(0));
		assertEquals("bar", results.get(1));	
	}
}
