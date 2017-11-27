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

package org.springframework.cloud.function.stream.config;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.function.stream.FunctionInvoker;
import org.springframework.cloud.stream.binding.StreamListenerParameterAdapter;
import org.springframework.cloud.stream.reactive.FluxSender;
import org.springframework.cloud.stream.reactive.MessageChannelToFluxSenderParameterAdapter;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.messaging.MessageChannel;
import org.springframework.util.ReflectionUtils;

/**
 * @author Dave Syer
 *
 */
public class MessageChannelToFunctionInvokerParameterAdapter
		implements StreamListenerParameterAdapter<FunctionInvoker, MessageChannel> {

	private StreamListeningFunctionInvoker invoker;
	private final BeanFactory beanFactory;
	private final MessageChannelToFluxSenderParameterAdapter adapter;

	public MessageChannelToFunctionInvokerParameterAdapter(
			BeanFactory beanFactory,
			MessageChannelToFluxSenderParameterAdapter adapter) {
		this.beanFactory = beanFactory;
		this.adapter = adapter;
	}

	@Override
	public boolean supports(Class<?> bindingTargetType, MethodParameter methodParameter) {
		ResolvableType type = ResolvableType.forMethodParameter(methodParameter);
		return MessageChannel.class.isAssignableFrom(bindingTargetType)
				&& FunctionInvoker.class.isAssignableFrom(type.getRawClass());
	}

	@Override
	public FunctionInvoker adapt(MessageChannel bindingTarget,
			MethodParameter parameter) {
		MethodParameter actual = new MethodParameter(
				ReflectionUtils.findMethod(getClass(), "dummy", FluxSender.class), 0);
		return input -> invoker().handle(input, adapter.adapt(bindingTarget, actual));
	}

	private StreamListeningFunctionInvoker invoker() {
		if (this.invoker==null) {
			// Lazy lookup of invoker to prevent cascade of instantiation 
			this.invoker = beanFactory.getBean(StreamListeningFunctionInvoker.class);
		}
		return this.invoker;
	}

	public void dummy(FluxSender sender) {

	}

}
