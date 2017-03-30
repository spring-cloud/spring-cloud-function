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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
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

	@PostMapping(path = { "/{name}", "/{name}/**" })
	public ResponseEntity<Flux<String>> function(@PathVariable String name,
			@RequestAttribute(name = "org.springframework.web.servlet.HandlerMapping.pathWithinHandlerMapping", required = false) String path,
			@RequestBody Flux<String> body) {
		String suffix = path.replace("/" + name, "");
		name = name + suffix;
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

	@GetMapping(path = { "/{name}", "/{name}/**" })
	public Object supplier(@PathVariable String name,
			@RequestAttribute(name = "org.springframework.web.servlet.HandlerMapping.pathWithinHandlerMapping", required = false) String path) {
		String suffix = path.replace("/" + name, "");
		if (StringUtils.hasText(suffix)) {
			while (suffix.startsWith("/")) {
				suffix = suffix.substring(1);
				Function<Flux<?>, Flux<?>> function = functions.lookupFunction(name);
				if (function != null) {
					return value(name, suffix);
				}
				int index = suffix.indexOf("/");
				name = name + (index > 0 ? "/" + suffix.substring(0, index) : "");
				suffix = index > 0 ? suffix.substring(index) : suffix;
			}
			suffix = "/" + suffix;
		}
		else {
			suffix = "";
		}
		return supplier(name + suffix);
	}

	@SuppressWarnings({ "unchecked" })
	private Flux<String> supplier(@PathVariable String name) {
		Supplier<Object> supplier = functions.lookupSupplier(name);
		if (supplier == null) {
			throw new IllegalArgumentException("no such supplier: " + name);
		}
		Flux<String> result = (Flux<String>) supplier.get();
		return debug ? result.log() : result;
	}

	private Mono<String> value(@PathVariable String name, @PathVariable String value) {
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
