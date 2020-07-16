package org.springframework.cloud.function.rsocket;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import reactor.util.retry.Retry;

class RSocketForwardingFunction implements Function<Message<byte[]>, Publisher<Message<byte[]>>> {
	private static Log logger = LogFactory.getLog(RSocketForwardingFunction.class);

	private final RSocket rSocket;

	private final FunctionInvocationWrapper targetFunction;

	RSocketForwardingFunction(FunctionInvocationWrapper targetFunction, InetSocketAddress outputAddress) {
		this.targetFunction = targetFunction;
		this.rSocket = outputAddress == null ? null
				: RSocketConnector.connectWith(TcpClientTransport.create(outputAddress))
					.log()
					.retryWhen(Retry.backoff(5, Duration.ofSeconds(1)))
					.block();
	}

	@Override
	public Publisher<Message<byte[]>> apply(Message<byte[]> input) {
		if (logger.isDebugEnabled()) {
			logger.debug("Executiing: " + this.targetFunction);
		}

		Object rawResult = this.targetFunction.apply(input);
		Publisher<Message<byte[]>> resultMessage = this.rSocket
				.requestStream(DefaultPayload.create(((Message<byte[]>) rawResult).getPayload()))
				.map(this::buildResultMessage);
		return resultMessage;
	}

	private Message<byte[]> buildResultMessage(Payload payload) {
		ByteBuffer payloadBuffer = payload.getData();
		byte[] payloadData = new byte[payloadBuffer.remaining()];
		payloadBuffer.get(payloadData);
		return MessageBuilder.withPayload(payloadData).build();
	}
}
