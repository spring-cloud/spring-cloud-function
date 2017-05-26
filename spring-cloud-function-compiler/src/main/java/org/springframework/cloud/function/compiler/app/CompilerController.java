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

package org.springframework.cloud.function.compiler.app;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Mark Fisher
 */
@RestController
public class CompilerController {

	private final CompiledFunctionRegistry registry = new CompiledFunctionRegistry();

	@PostMapping(path = "/supplier/{name}")
	public void registerSupplier(@PathVariable String name, @RequestBody String lambda,
			@RequestParam(defaultValue="Flux<String>") String type) {
		this.registry.registerSupplier(name, lambda, type);
	}

	@PostMapping(path = "/function/{name}")
	public void registerFunction(@PathVariable String name, @RequestBody String lambda,
			@RequestParam(defaultValue="Flux<String>") String inputType,
			@RequestParam(defaultValue="Flux<String>") String outputType) {
		this.registry.registerFunction(name, lambda, inputType, outputType);
	}

	@PostMapping(path = "/consumer/{name}")
	public void registerConsumer(@PathVariable String name, @RequestBody String lambda,
			@RequestParam(defaultValue="Flux<String>") String type) {
		this.registry.registerConsumer(name, lambda, type);
	}
}
