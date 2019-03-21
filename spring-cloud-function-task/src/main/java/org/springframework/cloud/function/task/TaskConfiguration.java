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

package org.springframework.cloud.function.task;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.registry.FunctionCatalog;
import org.springframework.cloud.task.configuration.EnableTask;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.core.publisher.Flux;

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
		final Supplier<Flux<Object>> supplier = registry
				.lookupSupplier(properties.getSupplier());
		final Function<Flux<Object>, Flux<Object>> function = registry
				.lookupFunction(properties.getFunction());
		final Consumer<Flux<Object>> consumer = registry
				.lookupConsumer(properties.getConsumer());
		CommandLineRunner runner = new CommandLineRunner() {

			@Override
			public void run(String... args) throws Exception {
				consumer.accept(function.apply(supplier.get()));
			}
		};
		return runner;
	}
}
