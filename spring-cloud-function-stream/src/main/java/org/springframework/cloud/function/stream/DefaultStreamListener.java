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

package org.springframework.cloud.function.stream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Default configuration class for listening to streams and invoking functions (and
 * consumers). If an application has an <code>@EnableBinding</code> for any other
 * interface it can copy this code and change the name of the interface (from
 * {@link Processor} to whatever is appropriate to the application).
 * 
 * @author Dave Syer
 *
 */
@Configuration
@EnableBinding(Processor.class)
@ConditionalOnProperty(prefix = "spring.cloud.function.stream", value = "defaultBindingsEnabled", matchIfMissing = true)
public class DefaultStreamListener {
	@StreamListener
	public Mono<Void> handle(@Input(Processor.INPUT) Flux<Message<?>> input,
			@Output(Processor.OUTPUT) FunctionInvoker output) {
		return output.send(input);
	}
}