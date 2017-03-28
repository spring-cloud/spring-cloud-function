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

package org.springframework.cloud.function.web;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.function.registry.FunctionCatalog;
import org.springframework.cloud.function.support.FluxSupplier;
import org.springframework.cloud.function.support.FunctionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Dave Syer
 * @author Mark Fisher
 */
@RestController
@ConditionalOnClass(RestController.class)
@RequestMapping("${spring.cloud.function.web.path:}")
public class FunctionController {

	@Value("${debug:${DEBUG:false}}")
	private boolean debug = false;

	private final FunctionCatalog functions;

	@Autowired
	public FunctionController(FunctionCatalog catalog) {
		this.functions = catalog;
	}

	@PostMapping(path = "/{name}")
	public ResponseEntity<Flux<String>> function(@PathVariable String name,
			@RequestBody Flux<String> body) {
		Function<Flux<?>, Flux<?>> function = functions.lookupFunction(name);
		if (function != null) {
			@SuppressWarnings("unchecked")
			Flux<String> result = (Flux<String>) function.apply(body);
			return ResponseEntity.ok().body(debug ? result.log() : result);
		}
		Consumer<Flux<?>> consumer = functions.lookupConsumer(name);
		if (consumer != null) {
			body = body.cache(); // send a copy back to the caller
			consumer.accept(body);
			return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
		}
		throw new IllegalArgumentException("no such function: " + name);
	}

	@GetMapping(path = "/{name}")
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Flux<String> supplier(@PathVariable String name) {
		Supplier<Object> supplier = functions.lookupSupplier(name);
		if (supplier == null) {
			throw new IllegalArgumentException("no such supplier: " + name);
		}
		if (!FunctionUtils.isFluxSupplier(supplier)) {
			supplier = new FluxSupplier(supplier);
		}
		Flux<String> result = (Flux<String>) supplier.get();
		return debug ? result.log() : result;
	}

	@GetMapping(path = "/{name}/{value}")
	public Mono<String> value(@PathVariable String name, @PathVariable String value) {
		Function<Flux<?>, Flux<?>> function = functions.lookupFunction(name);
		if (function != null) {
			@SuppressWarnings({ "unchecked" })
			Mono<String> result = Mono
					.from((Flux<String>) function.apply(Flux.just(value)));
			return debug ? result.log() : result;
		}
		throw new IllegalArgumentException("no such function: " + name);
	}
}
