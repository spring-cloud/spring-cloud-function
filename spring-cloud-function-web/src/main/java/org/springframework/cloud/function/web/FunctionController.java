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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.function.support.FluxSupplier;
import org.springframework.cloud.function.support.FunctionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Dave Syer
 * @author Mark Fisher
 */
@Component
public class FunctionController {

	@Value("${debug:${DEBUG:false}}")
	private boolean debug = false;
	
	@PostMapping(path = "/**")
	@ResponseBody
	public ResponseEntity<Flux<String>> post(
			@RequestAttribute(required = false, name = "org.springframework.cloud.function.web.FunctionHandlerMapping.function") Function<Flux<?>, Flux<?>> function,
			@RequestAttribute(required = false, name = "org.springframework.cloud.function.web.FunctionHandlerMapping.consumer") Consumer<Flux<?>> consumer,
			@RequestBody Flux<String> body) {
		if (function != null) {
			@SuppressWarnings("unchecked")
			Flux<String> result = (Flux<String>) function.apply(body);
			return ResponseEntity.ok().body(debug ? result.log() : result);
		}
		if (consumer != null) {
			body = body.cache(); // send a copy back to the caller
			consumer.accept(body);
			return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
		}
		throw new IllegalArgumentException("no such function");
	}

	@GetMapping(path = "/**")
	@ResponseBody
	public Object get(
			@RequestAttribute(required = false, name = "org.springframework.cloud.function.web.FunctionHandlerMapping.function") Function<Flux<?>, Flux<?>> function,
			@RequestAttribute(required = false, name = "org.springframework.cloud.function.web.FunctionHandlerMapping.supplier") Supplier<Flux<?>> supplier,
			@RequestAttribute(required = false, name = "org.springframework.cloud.function.web.FunctionHandlerMapping.argument") String argument,
			@RequestAttribute("org.springframework.web.servlet.HandlerMapping.pathWithinHandlerMapping") String path) {
		if (function != null) {
			return value(function, argument);
		}
		return supplier(supplier);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Flux<String> supplier(Supplier<Flux<?>> supplier) {
		if (!FunctionUtils.isFluxSupplier(supplier)) {
			supplier = new FluxSupplier(supplier);
		}
		Flux<String> result = (Flux<String>) supplier.get();
		return debug ? result.log() : result;
	}

	private Mono<String> value(Function<Flux<?>, Flux<?>> function,
			@PathVariable String value) {
		@SuppressWarnings({ "unchecked" })
		Mono<String> result = Mono.from((Flux<String>) function.apply(Flux.just(value)));
		return debug ? result.log() : result;
	}
}
