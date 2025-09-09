/*
 * Copyright 2021-present the original author or authors.
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

package org.springframework.cloud.function.grpc;

import java.util.function.Function;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.MessageRoutingCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.2
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FunctionGrpcProperties.class)
public class GrpcFunctionAutoConfiguration {
	
	public static String GRPC_INVOKER_FUNCTION = "grpcInvokerFunction";
	
	public static String GRPC = "grpc";
	
	public static String GRPC_HOST = "grpcHost";
	
	public static String GRPC_PORT = "grpcPort";

	@Bean
	public Function<Message<byte[]>, Message<?>> grpcInvokerFunction() {
		return message -> {
			if (message.getHeaders().containsKey(GRPC_HOST)) {
				String host = (String) message.getHeaders().get(GRPC_HOST);
				int port  = message.getHeaders().get(GRPC_PORT) instanceof String stringPort 
						? Integer.parseInt(stringPort) 
								: (int)message.getHeaders().get(GRPC_PORT);
				
				return GrpcUtils.requestReply(host, port, message);
			}
			return GrpcUtils.requestReply(message);
		};
	}
	
	@Bean
	public MessageRoutingCallback routingCallback() {
		return new MessageRoutingCallback() {
			public String routingResult(Message<?> message) {
				if (message.getHeaders().containsKey(FunctionProperties.PROXY) 
						&& message.getHeaders().get(FunctionProperties.PROXY).equals(GRPC)) {
					return GRPC_INVOKER_FUNCTION;
				}
				return null;
			}
		};
	}
}
