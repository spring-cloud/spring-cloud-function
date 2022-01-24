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

import com.example.functions.CharCounter;
import com.example.functions.Exclaimer;
import com.example.functions.Greeter;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionTests {

	private final SampleApplication functions = new SampleApplication();

	@Test
	public void testUppercase() {
		String output = this.functions.uppercase().apply("foobar");
		assertThat(output).isEqualTo("FOOBAR");
	}

	@Test
	public void testLowercase() {
		Flux<String> output = this.functions.lowercase().apply(Flux.just("FOO", "BAR"));
		List<String> results = output.collectList().block();
		assertThat(results.size()).isEqualTo(2);
		assertThat(results.get(0)).isEqualTo("foo");
		assertThat(results.get(1)).isEqualTo("bar");
	}

	@Test
	public void testHello() {
		String output = this.functions.hello().get();
		assertThat(output).isEqualTo("hello");
	}

	@Test
	public void testGreeter() {
		assertThat(new Greeter().apply("World")).isEqualTo("Hello World");
	}

	@Test
	public void testExclaimer() {
		Flux<String> input = Flux.just("foo", "bar");
		Flux<String> output = new Exclaimer().apply(input);
		List<String> results = output.collectList().block();
		assertThat(results.size()).isEqualTo(2);
		assertThat(results.get(0)).isEqualTo("foo!!!");
		assertThat(results.get(1)).isEqualTo("bar!!!");
	}

	@Test
	public void testCharCounter() {
		assertThat(new CharCounter().apply("this is 21 chars long"))
			.isEqualTo((Integer) 21);
	}

}
