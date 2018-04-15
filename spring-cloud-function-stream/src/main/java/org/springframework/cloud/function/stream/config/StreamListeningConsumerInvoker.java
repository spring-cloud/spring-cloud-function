/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.stream.config;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.converter.CompositeMessageConverterFactory;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.messaging.Message;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 */
public class StreamListeningConsumerInvoker extends AbstractStreamListeningInvoker {

	public StreamListeningConsumerInvoker(FunctionCatalog functionCatalog,
			FunctionInspector functionInspector,
			CompositeMessageConverterFactory converterFactory, String defaultRoute,
			boolean share) {
		super(functionCatalog, functionInspector, converterFactory, defaultRoute, share);
	}

	@StreamListener
	public void handle(@Input(Processor.INPUT) Flux<Message<?>> input) {
		input.groupBy(this::select).flatMap(group -> group.key().process(group))
				.subscribe();
	}

}
