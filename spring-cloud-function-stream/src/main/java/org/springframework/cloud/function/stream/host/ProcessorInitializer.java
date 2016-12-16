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

package org.springframework.cloud.function.stream.host;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.function.stream.processor.FunctionConfigurationProperties;
import org.springframework.cloud.function.stream.processor.ProcessorConfiguration;
import org.springframework.cloud.stream.binding.BindingService;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;

public class ProcessorInitializer implements ApplicationListener<ApplicationEvent> {

	@Autowired
	private FunctionConfigurationProperties properties;

	private final AtomicBoolean host = new AtomicBoolean();

	private final AtomicBoolean done = new AtomicBoolean();

	private ConfigurableApplicationContext context;

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (this.done.get()) {
			return;
		}
		if (!this.host.get() && event instanceof ContextRefreshedEvent) {
			this.context = (ConfigurableApplicationContext) ((ContextRefreshedEvent) event).getApplicationContext();
			if (this.context.getParent() != null && this.context.getParent().getParent() == null) {
				this.host.set(true);
				if (properties.getName() != null) {
					createProcessorContext();
				}
			}
		}
		if (this.host.get() && event instanceof EnvironmentChangeEvent) {
			if (((EnvironmentChangeEvent) event).getKeys().contains("function.name") && properties.getName() != null) {
				createProcessorContext();
			}
		}
	}

	public void createProcessorContext() {
		boolean proceed = this.done.compareAndSet(false, true);
		if (!proceed) {
			return;
		}
		BindingService bindingService = this.context.getBean(BindingService.class);
		bindingService.getBindingServiceProperties().getBindings().get("input").setDestination(properties.getInput());
		bindingService.getBindingServiceProperties().getBindings().get("output").setDestination(properties.getOutput());
		new SpringApplicationBuilder(ProcessorConfiguration.class)
				.parent(this.context)
				.web(false)
				.run("--spring.cloud.stream.bindings.input.destination=" + properties.getInput(),
						"--spring.cloud.stream.bindings.output.destination=" + properties.getOutput(),
						"--function.name=" + properties.getName());
	}
}
