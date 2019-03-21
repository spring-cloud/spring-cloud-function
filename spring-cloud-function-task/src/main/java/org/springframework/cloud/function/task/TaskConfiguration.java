/*
 * Copyright 2016 the original author or authors.
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.springframework.util.StringUtils;

import reactor.core.publisher.Flux;

/**
 * @author Mark Fisher
 */
@Configuration
@EnableTask
@EnableConfigurationProperties(LambdaConfigurationProperties.class)
@ConditionalOnClass({ EnableTask.class })
public class TaskConfiguration {

	@Autowired
	private LambdaConfigurationProperties properties;

	@Bean
	public CommandLineRunner commandLineRunner(FunctionCatalog registry) {
		final Supplier<Flux<Object>> supplier = registry
				.lookupSupplier(properties.getSupplier());
		String functionName = properties.getFunction();
		Function<Flux<Object>, Flux<Object>> function = registry
				.lookupFunction(functionName);
		final Consumer<Object> consumer = registry
				.lookupConsumer(properties.getConsumer());
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean status = new AtomicBoolean();
		CommandLineRunner runner = new CommandLineRunner() {

			@Override
			public void run(String... args) throws Exception {
				function.apply(supplier.get()).subscribe(consumer,
						new CompletionConsumer(latch, status, false),
						new CompletionConsumer(latch, status, true));
				latch.await();
			}
		};
		return runner;
	}

	private static class CompletionConsumer implements Consumer<Throwable>, Runnable {

		private final CountDownLatch latch;

		private final AtomicBoolean status;

		private final boolean value;

		private CompletionConsumer(CountDownLatch latch, AtomicBoolean status,
				boolean value) {
			this.latch = latch;
			this.status = status;
			this.value = value;
		}

		@Override
		public void accept(Throwable t) {
			System.err.println("task failed: " + t);
			this.run();
		}

		@Override
		public void run() {
			this.status.set(this.value);
			this.latch.countDown();
		}
	}
}
