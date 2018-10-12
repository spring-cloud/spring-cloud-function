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
package org.springframework.cloud.function.adapter.azure;

import java.io.IOException;
import java.util.function.Function;

import org.junit.After;
import org.junit.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
public class AzureSpringFunctionInitializerTests {

	private AzureSpringFunctionInitializer handler = null;

	<I, O> AzureSpringFunctionInitializer handler(Class<?> config) {
		AzureSpringFunctionInitializer handler = new AzureSpringFunctionInitializer(
				config);
		this.handler = handler;
		return handler;
	}

	@After
	public void close() throws IOException {
		if (handler != null)
			handler.close();
	}

	@Test
	public void bareConfig() {
		AzureSpringFunctionInitializer handler = handler(BareConfig.class);
		handler.initialize(new TestExecutionContext("uppercase"));
		Bar bar = (Bar) Flux.from(handler.getFunction().apply(Flux.just(new Foo("bar"))))
				.blockFirst();
		assertThat(bar.getValue()).isEqualTo("BAR");
	}

	@Test
	public void initializer() {
		AzureSpringFunctionInitializer handler = handler(InitializerConfig.class);
		handler.initialize(new TestExecutionContext("uppercase"));
		Bar bar = (Bar) Flux.from(handler.getFunction().apply(Flux.just(new Foo("bar"))))
				.blockFirst();
		assertThat(bar.getValue()).isEqualTo("BAR");
	}

	@Test
	public void function() {
		AzureSpringFunctionInitializer handler = handler(FunctionConfig.class);
		handler.initialize(new TestExecutionContext("uppercase"));
		Bar bar = (Bar) Flux.from(handler.getFunction().apply(Flux.just(new Foo("bar"))))
				.blockFirst();
		assertThat(bar.getValue()).isEqualTo("BAR");
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class BareConfig {
		@Bean
		public Function<Foo, Bar> function() {
			return foo -> new Bar(foo.getValue().toUpperCase());
		}
	}

	@SpringBootConfiguration
	protected static class FunctionConfig implements Function<Foo, Bar> {
		@Override
		public Bar apply(Foo foo) {
			return new Bar(foo.getValue().toUpperCase());
		}
	}

	@SpringBootConfiguration
	protected static class InitializerConfig
			implements ApplicationContextInitializer<GenericApplicationContext> {

		public Function<Foo, Bar> function() {
			return foo -> new Bar(foo.getValue().toUpperCase());
		}

		@Override
		public void initialize(GenericApplicationContext context) {
			context.registerBean(FunctionRegistration.class,
					() -> new FunctionRegistration<Function<Foo, Bar>>(function(),
							"uppercase")
									.type(FunctionType.from(Foo.class).to(Bar.class)));
		}
	}
}
