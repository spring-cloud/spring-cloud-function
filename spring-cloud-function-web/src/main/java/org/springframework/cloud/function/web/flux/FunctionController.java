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

package org.springframework.cloud.function.web.flux;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.function.context.FunctionInspector;
import org.springframework.cloud.function.web.flux.request.FluxRequest;
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

	private static Log logger = LogFactory.getLog(FunctionController.class);

	private FunctionInspector inspector;

	@Value("${debug:${DEBUG:false}}")
	private boolean debug = false;

	public FunctionController(FunctionInspector inspector) {
		this.inspector = inspector;
	}

	@PostMapping(path = "/**")
	@ResponseBody
	public ResponseEntity<Flux<?>> post(
			@RequestAttribute(required = false, name = "org.springframework.cloud.function.web.flux.FunctionHandlerMapping.function") Function<Flux<?>, Flux<?>> function,
			@RequestAttribute(required = false, name = "org.springframework.cloud.function.web.flux.FunctionHandlerMapping.consumer") Consumer<Flux<?>> consumer,
			@RequestBody FluxRequest<?> body) {
		if (function != null) {
			Flux<?> result = (Flux<?>) function.apply(body.flux());
			if (logger.isDebugEnabled()) {
				logger.debug("Handled POST with function");
			}
			return ResponseEntity.ok().body(debug ? result.log() : result);
		}
		if (consumer != null) {
			Flux<?> flux = body.flux().cache(); // send a copy back to the caller
			consumer.accept(flux);
			if (logger.isDebugEnabled()) {
				logger.debug("Handled POST with consumer");
			}
			return ResponseEntity.status(HttpStatus.ACCEPTED).body(flux);
		}
		throw new IllegalArgumentException("no such function");
	}

	@GetMapping(path = "/**")
	@ResponseBody
	public Object get(
			@RequestAttribute(required = false, name = "org.springframework.cloud.function.web.flux.FunctionHandlerMapping.function") Function<Flux<?>, Flux<?>> function,
			@RequestAttribute(required = false, name = "org.springframework.cloud.function.web.flux.FunctionHandlerMapping.supplier") Supplier<Flux<?>> supplier,
			@RequestAttribute(required = false, name = "org.springframework.cloud.function.web.flux.FunctionHandlerMapping.argument") String argument) {
		if (function != null) {
			return value(function, argument);
		}
		return supplier(supplier);
	}

	private Flux<?> supplier(Supplier<Flux<?>> supplier) {
		Flux<?> result = supplier.get();
		if (logger.isDebugEnabled()) {
			logger.debug("Handled GET with supplier");
		}
		return debug ? result.log() : result;
	}

	private Mono<?> value(Function<Flux<?>, Flux<?>> function,
			@PathVariable String value) {
		Object input = inspector.convert(inspector.getName(function), value);
		Mono<?> result = Mono.from(function.apply(Flux.just(input)));
		if (logger.isDebugEnabled()) {
			logger.debug("Handled GET with function");
		}
		return debug ? result.log() : result;
	}
}
