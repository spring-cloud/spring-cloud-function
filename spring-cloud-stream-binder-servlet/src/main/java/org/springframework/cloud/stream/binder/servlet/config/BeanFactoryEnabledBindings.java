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
package org.springframework.cloud.stream.binder.servlet.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.binder.servlet.EnabledBindings;
import org.springframework.cloud.stream.binding.BindingBeanDefinitionRegistryUtils;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ReflectionUtils;

/**
 * @author Dave Syer
 *
 */
public class BeanFactoryEnabledBindings implements EnabledBindings {

	private final ConfigurableListableBeanFactory beanFactory;
	private final AtomicBoolean initialized = new AtomicBoolean(false);
	private final Map<String, String> outputsToInputs = new HashMap<>();
	private final Set<String> outputs = new HashSet<>();
	private final Set<String> inputs = new HashSet<>();
	private final BindingServiceProperties binding;

	public BeanFactoryEnabledBindings(ConfigurableListableBeanFactory beanFactory,
			BindingServiceProperties binding) {
		this.beanFactory = beanFactory;
		this.binding = binding;
	}

	@Override
	public Set<String> getInputs() {
		init();
		return this.inputs;
	}

	@Override
	public Set<String> getOutputs() {
		init();
		return this.outputs;
	}

	@Override
	public String getInput(String output) {
		init();
		return outputsToInputs.get(output);
	}

	private void init() {
		if (initialized.compareAndSet(false, true)) {
			String[] names = beanFactory.getBeanNamesForAnnotation(EnableBinding.class);
			for (String bean : names) {
				Class<?> type = beanFactory.getType(bean);
				MultiValueMap<String, Object> attrs = AnnotatedElementUtils
						.getAllAnnotationAttributes(type, EnableBinding.class.getName());
				List<Object> list = attrs.get("value");
				if (list != null) {
					for (Object object : list) {
						Class<?>[] bindings = (Class<?>[]) object;
						for (Class<?> binding : bindings) {
							List<String> inputs = new ArrayList<>();
							List<String> outputs = new ArrayList<>();
							ReflectionUtils.doWithMethods(binding, method -> {
								Input input = AnnotationUtils.findAnnotation(method,
										Input.class);
								Output output = AnnotationUtils.findAnnotation(method,
										Output.class);
								if (input != null) {
									String name = BindingBeanDefinitionRegistryUtils
											.getBindingTargetName(input, method);
									inputs.add(BeanFactoryEnabledBindings.this.binding
											.getBindingDestination(name));
								}
								if (output != null) {
									String name = BindingBeanDefinitionRegistryUtils
											.getBindingTargetName(output, method);
									outputs.add(BeanFactoryEnabledBindings.this.binding
											.getBindingDestination(name));
								}
							});
							BeanFactoryEnabledBindings.this.outputs.addAll(outputs);
							BeanFactoryEnabledBindings.this.inputs.addAll(inputs);
							if (inputs.size() == 1 && outputs.size() == 1) {
								BeanFactoryEnabledBindings.this.outputsToInputs
										.put(outputs.get(0), inputs.get(0));
							}
						}
					}
				}
			}
		}
	}

}