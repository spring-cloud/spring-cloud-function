/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.cloud.function.registry;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;

import reactor.core.publisher.Flux;

/**
 * @author Mark Fisher
 */
public class FileSystemFunctionRegistryTests {

	private File directory;

	@Before
	public void init() throws IOException {
		this.directory = new File("/tmp/file-system-function-registry-tests");
		this.directory.mkdirs();
		this.directory.deleteOnExit();
	}

	@Test
	public void registerAndLookup() throws IOException {
		FileSystemFunctionRegistry registry = new FileSystemFunctionRegistry(this.directory);
		registry.register("uppercase", "f->f.map(s->s.toString().toUpperCase())");
		Function<Flux<String>, Flux<String>> function = registry.lookup("uppercase");
		Flux<String> output = function.apply(Flux.just("foo", "bar"));
		List<String> results = output.collectList().block();
		assertEquals("FOO", results.get(0));
		assertEquals("BAR", results.get(1));
	}

	@Test
	public void compose() throws IOException {
		FileSystemFunctionRegistry registry = new FileSystemFunctionRegistry(this.directory);
		registry.register("uppercase", "f->f.map(s->s.toString().toUpperCase())");
		registry.register("exclaim", "f->f.map(s->s+\"!!!\")");
		Function<Flux<String>, Flux<String>> function = registry.compose("uppercase", "exclaim");
		Flux<String> output = function.apply(Flux.just("foo", "bar"));
		List<String> results = output.collectList().block();
		assertEquals("FOO!!!", results.get(0));
		assertEquals("BAR!!!", results.get(1));
	}
}
