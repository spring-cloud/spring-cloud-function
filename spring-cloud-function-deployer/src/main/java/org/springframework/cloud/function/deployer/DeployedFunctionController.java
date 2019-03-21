/*
 * Copyright 2016-2017 the original author or authors.
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
package org.springframework.cloud.function.deployer;

import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
@RestController
public class DeployedFunctionController {

	private final FunctionExtractingAppDeployer deployer;

	@Autowired
	public DeployedFunctionController(FunctionExtractingAppDeployer deployer) {
		this.deployer = deployer;
	}

	@PostMapping(path = "/{name}", consumes = MediaType.TEXT_PLAIN_VALUE)
	public Flux<String> function(@PathVariable String name,
			@RequestBody Flux<String> body) {
		Function<Object, Object> function;
		if (name.contains(",")) {
			function = deployer.composeFunction(name.split(","));
		}
		else {
			function = deployer.lookupFunction(name);
		}
		@SuppressWarnings("unchecked")
		Flux<String> result = (Flux<String>) function.apply(body);
		return result;
	}

	@GetMapping("/{name}")
	public Flux<String> supplier(@PathVariable String name) {
		@SuppressWarnings("unchecked")
		Flux<String> result = (Flux<String>) deployer.lookupSupplier(name).get();
		return result;
	}

}
