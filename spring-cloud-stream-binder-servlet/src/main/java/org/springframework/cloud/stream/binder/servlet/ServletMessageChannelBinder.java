/*
 * Copyright 2014-2016 the original author or authors.
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

package org.springframework.cloud.stream.binder.servlet;

import org.springframework.cloud.stream.binder.AbstractBinder;
import org.springframework.cloud.stream.binder.Binding;
import org.springframework.cloud.stream.binder.ConsumerProperties;
import org.springframework.cloud.stream.binder.DefaultBinding;
import org.springframework.cloud.stream.binder.ProducerProperties;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;

/**
 * A {@link org.springframework.cloud.stream.binder.Binder} implementation backed by HTTP.
 *
 * @author Dave Syer
 */
public class ServletMessageChannelBinder
		extends AbstractBinder<MessageChannel, ConsumerProperties, ProducerProperties> {

	private MessageController controller;

	public ServletMessageChannelBinder(MessageController controller) {
		this.controller = controller;
	}

	@Override
	protected Binding<MessageChannel> doBindConsumer(String name, String group,
			MessageChannel inputTarget, ConsumerProperties properties) {
		controller.bind(name, group, inputTarget);
		return new DefaultBinding<MessageChannel>(name, group, inputTarget, null);
	}

	@Override
	protected Binding<MessageChannel> doBindProducer(String name,
			MessageChannel outboundBindTarget, ProducerProperties properties) {
		controller.subscribe(name, (SubscribableChannel) outboundBindTarget);
		return new DefaultBinding<MessageChannel>(name, null, outboundBindTarget, null);
	}

}
