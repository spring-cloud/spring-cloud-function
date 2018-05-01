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

package org.springframework.cloud.function.adapter.openwhisk;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 * @author Mark Fisher
 * @author Kamesh Sampath
 */
public class OpenWhiskFunctionInitializer {

	private static Log logger = LogFactory.getLog(OpenWhiskFunctionInitializer.class);

	private Function<Publisher<?>, Publisher<?>> function;

	private Consumer<Publisher<?>> consumer;

	private Supplier<Publisher<?>> supplier;

	private AtomicBoolean initialized = new AtomicBoolean();

	@Autowired(required = false)
	private FunctionInspector inspector;

	@Autowired
	private FunctionCatalog catalog;

	@Autowired
	private FunctionProperties properties;

	protected void initialize() {
		logger.info("Initializing - OpenWhisk Function Initializer");
		if (!this.initialized.compareAndSet(false, true)) {
			return;
		}
		String name = this.properties.getName();
		String type = this.properties.getType();
		if ("function".equals(type)) {
			this.function = this.catalog.lookup(Function.class, name);
		}
		else if ("consumer".equals(type)) {
			this.consumer = this.catalog.lookup(Consumer.class, name);
		}
		else if ("supplier".equals(type)) {
			this.supplier = this.catalog.lookup(Supplier.class, name);
		}
	}

	protected Class<?> getInputType() {
		if (inspector != null) {
			return inspector.getInputType(function());
		}
		return Object.class;
	}

	private Object function() {
		return this.function != null ? this.function
				: (this.consumer != null ? this.consumer : this.supplier);
	}

	protected Publisher<?> apply(Publisher<?> input) {
		if (this.function != null) {
			return function.apply(input);
		}
		if (this.consumer != null) {
			this.consumer.accept(input);
			return Flux.empty();
		}
		if (this.supplier != null) {
			return this.supplier.get();
		}
		throw new IllegalStateException("No function defined");
	}
}
