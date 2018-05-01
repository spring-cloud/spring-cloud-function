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

package org.springframework.cloud.function.task;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.task.configuration.EnableTask;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.core.publisher.Mono;

/**
 * @author Mark Fisher
 */
@Configuration
@EnableTask
@EnableConfigurationProperties(TaskConfigurationProperties.class)
@ConditionalOnClass({ EnableTask.class })
public class TaskConfiguration {

	@Autowired
	private TaskConfigurationProperties properties;

	@Bean
	public CommandLineRunner commandLineRunner(FunctionCatalog registry) {
		final Supplier<Publisher<Object>> supplier = registry.lookup(Supplier.class,
				properties.getSupplier());
		final Function<Publisher<Object>, Publisher<Object>> function = registry
				.lookup(Function.class, properties.getFunction());
		final Consumer<Publisher<Object>> consumer = consumer(registry);
		CommandLineRunner runner = new CommandLineRunner() {

			@Override
			public void run(String... args) throws Exception {
				consumer.accept(function.apply(supplier.get()));
			}
		};
		return runner;
	}

	private Consumer<Publisher<Object>> consumer(FunctionCatalog registry) {
		Consumer<Publisher<Object>> consumer = registry.lookup(Consumer.class,
				properties.getConsumer());
		if (consumer != null) {
			return consumer;
		}
		Function<Publisher<Object>, Publisher<Void>> function = registry.lookup(Function.class,
				properties.getConsumer());
		return flux -> Mono.from(function.apply(flux)).subscribe();
	}
}
