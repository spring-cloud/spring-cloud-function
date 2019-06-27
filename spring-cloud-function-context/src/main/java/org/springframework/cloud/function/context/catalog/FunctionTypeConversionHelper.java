package org.springframework.cloud.function.context.catalog;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.core.convert.ConversionService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.ObjectUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuple5;
import reactor.util.function.Tuple6;
import reactor.util.function.Tuple7;
import reactor.util.function.Tuple8;
import reactor.util.function.Tuples;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
class FunctionTypeConversionHelper {

	private static Log logger = LogFactory.getLog(FunctionTypeConversionHelper.class);

	private final FunctionRegistration<?> functionRegistration;

	private final Type[] functionArgumentTypes;

	private final ConversionService conversionService;

	private final MessageConverter messageConverter;

	FunctionTypeConversionHelper(FunctionRegistration<?> functionRegistration, ConversionService conversionService, MessageConverter messageConverter) {
		this.conversionService = conversionService;
		this.messageConverter = messageConverter;
		this.functionRegistration = functionRegistration;
		if ((this.functionRegistration.getType().getType()) instanceof ParameterizedType) {
			this.functionArgumentTypes = ((ParameterizedType)this.functionRegistration.getType().getType()).getActualTypeArguments();
		}
		else {
			this.functionArgumentTypes  = new Type[] {this.functionRegistration.getType().getInputType()};
		}
	}


	@SuppressWarnings("rawtypes")
	Object convertInputIfNecessary(Object input) {
		List<Object> convertedResults = new ArrayList<Object>();
		if (input instanceof Tuple2) {
			convertedResults.add(this.doConvert(((Tuple2)input).getT1(), getInputArgumentType(0)));
			convertedResults.add(this.doConvert(((Tuple2)input).getT2(), getInputArgumentType(1)));
		}
		if (input instanceof Tuple3) {
			convertedResults.add(this.doConvert(((Tuple3)input).getT3(), getInputArgumentType(2)));
		}
		if (input instanceof Tuple4) {
			convertedResults.add(this.doConvert(((Tuple4)input).getT4(), getInputArgumentType(3)));
		}
		if (input instanceof Tuple5) {
			convertedResults.add(this.doConvert(((Tuple5)input).getT5(), getInputArgumentType(4)));
		}
		if (input instanceof Tuple6) {
			convertedResults.add(this.doConvert(((Tuple6)input).getT6(), getInputArgumentType(5)));
		}
		if (input instanceof Tuple7) {
			convertedResults.add(this.doConvert(((Tuple7)input).getT7(), getInputArgumentType(6)));
		}
		if (input instanceof Tuple8) {
			convertedResults.add(this.doConvert(((Tuple8)input).getT8(), getInputArgumentType(7)));
		}

		input = CollectionUtils.isEmpty(convertedResults)
				? this.doConvert(input, getInputArgumentType(0))
						: Tuples.fromArray(convertedResults.toArray());
		return input;
	}

	@SuppressWarnings("rawtypes")
	Object convertOutputIfNecessary(Object output, MimeType... acceptedOutputTypes) {
		if (ObjectUtils.isEmpty(acceptedOutputTypes)) {
			return output;
		}
		List<Object> convertedResults = new ArrayList<Object>();
		if (output instanceof Tuple2) {
			convertedResults.add(this.doConvert(((Tuple2)output).getT1(), acceptedOutputTypes[0]));
			convertedResults.add(this.doConvert(((Tuple2)output).getT2(), acceptedOutputTypes[1]));
		}
		if (output instanceof Tuple3) {
			convertedResults.add(this.doConvert(((Tuple3)output).getT3(), acceptedOutputTypes[2]));
		}
		if (output instanceof Tuple4) {
			convertedResults.add(this.doConvert(((Tuple4)output).getT4(), acceptedOutputTypes[3]));
		}
		if (output instanceof Tuple5) {
			convertedResults.add(this.doConvert(((Tuple5)output).getT5(), acceptedOutputTypes[4]));
		}
		if (output instanceof Tuple6) {
			convertedResults.add(this.doConvert(((Tuple6)output).getT6(), acceptedOutputTypes[5]));
		}
		if (output instanceof Tuple7) {
			convertedResults.add(this.doConvert(((Tuple7)output).getT7(), acceptedOutputTypes[6]));
		}
		if (output instanceof Tuple8) {
			convertedResults.add(this.doConvert(((Tuple8)output).getT8(), acceptedOutputTypes[7]));
		}

		output = Tuples.fromArray(convertedResults.toArray());

		return output;
	}

	int getInputArgumentCount() {
		Type[] types = ((ParameterizedType)this.functionArgumentTypes[0]).getActualTypeArguments();
		return types.length;
	}

	Object getInputArgument(int index) {
		return 0;
	}

	Class<?> getInputArgumentRawType(int index) {
		Type[] types = ((ParameterizedType)this.functionArgumentTypes[0]).getActualTypeArguments();
		return (Class<?>) ((ParameterizedType)types[index]).getRawType();
	}

	Type getInputArgumentType(int index) {
		if (this.functionArgumentTypes[0] instanceof ParameterizedType) {
			Type[] types = ((ParameterizedType)this.functionArgumentTypes[0]).getActualTypeArguments();

			return (types[index]);//.getActualTypeArguments()[0];
		}
		else {
			return this.functionArgumentTypes[0];
		}
	}

	Type getOutputArgumentType(int index) {
		if (this.functionArgumentTypes[1] instanceof ParameterizedType) {
			Type[] types = ((ParameterizedType)this.functionArgumentTypes[1]).getActualTypeArguments();

			return (types[index]);//.getActualTypeArguments()[0];
		}
		else {
			return this.functionArgumentTypes[1];
		}
	}

	private Class<?> getRawType(Type targetType) {
		if (targetType instanceof ParameterizedType && Publisher.class.isAssignableFrom((Class<?>)((ParameterizedType)targetType).getRawType())) {
			targetType = ((ParameterizedType)targetType).getActualTypeArguments()[0];
		}
		else if (targetType instanceof ParameterizedType && Message.class.isAssignableFrom((Class<?>)((ParameterizedType)targetType).getRawType())) {
			targetType = ((ParameterizedType)targetType).getActualTypeArguments()[0];
		}
		return (Class<?>) targetType;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object doConvert(Object incoming, Type targetType) {
		Class<?> actualType = this.getRawType(targetType);
		if (incoming instanceof Publisher) {
			if (!actualType.isAssignableFrom(Void.class)) {
				incoming = incoming instanceof Mono
						? Mono.from((Publisher) incoming).map(value -> this.doConvertArgument(value, targetType, actualType))
								: Flux.from((Publisher) incoming).map(value -> this.doConvertArgument(value, targetType, actualType));
			}
		}
		else {
			Assert.isTrue(!Publisher.class.isAssignableFrom(this.functionRegistration.getType().getInputWrapper()),
					"Invoking reactive function as imperative is not allowed. Function name(s): "
							+ this.functionRegistration.getNames());
			incoming = this.doConvertArgument(incoming, targetType, actualType);
		}
		return incoming;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object doConvert(Object incoming, MimeType mimeType) {
		MessageHeaders headers = new MessageHeaders(Collections.singletonMap(MessageHeaders.CONTENT_TYPE, mimeType));
		if (incoming instanceof Publisher) {
			incoming = incoming instanceof Mono
					? Mono.from((Publisher) incoming).map(value -> this.messageConverter.toMessage(value, headers))
							: Flux.from((Publisher) incoming).map(value -> this.messageConverter.toMessage(value, headers));
		}
		else {
			Assert.isTrue(!Publisher.class.isAssignableFrom(this.functionRegistration.getType().getInputWrapper()),
					"Invoking reactive function as imperative is not allowed. Function name(s): "
							+ this.functionRegistration.getNames());
			incoming = this.messageConverter.toMessage(incoming, headers);
		}
		return incoming;
	}

	private Object doConvertArgument(Object incomingValue, Type targetType, Class<?> actualInputType) {
		if (!Void.class.isAssignableFrom(actualInputType)) {
			if (incomingValue instanceof Message<?>) {
				incomingValue = this.isMessage(targetType)
						? this.fromMessageToMessage((Message<?>) incomingValue, actualInputType)
								: this.fromMessageToValue((Message<?>) incomingValue, actualInputType);
			}
			else {
				if (!incomingValue.getClass().isAssignableFrom(actualInputType)) {
					Assert.isTrue(this.conversionService.canConvert(incomingValue.getClass(), actualInputType),
							"Failed to convert value of type " + incomingValue.getClass() + " to " + targetType);
					incomingValue = this.conversionService.convert(incomingValue, actualInputType);
				}
			}
		}
		else {
			incomingValue = null;
		}
		return incomingValue;
	}

	private boolean isMessage(Type targetType) {
		if (targetType instanceof ParameterizedType) {
			return Message.class.isAssignableFrom((Class<?>)((ParameterizedType)targetType).getRawType());
		}
		return false;
	}

	/*
	 * Will conditionally convert Message's payload to a
	 * targetType unless such payload is already of that type.
	 */
	private Object fromMessageToValue(Message<?> incomingMessage, Class<?> targetType) {
		Object incomingValue = ((Message<?>)incomingMessage).getPayload();
		if (!incomingValue.getClass().isAssignableFrom(targetType)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Converting message '" + incomingMessage + "' with payload of type '" + incomingMessage.getPayload().getClass().getName()
						+ "' to value of type '" + targetType.getName()
						+ "' for invocation of " + functionRegistration.getNames());
			}
			incomingValue = this.messageConverter.fromMessage((Message<?>) incomingMessage, targetType);
		}
		return incomingValue;
	}

	/*
	 * Will conditionally convert Message's payload to a
	 * targetType unless such payload is already of that type
	 * wrapping the result of conversion into a Message with converted type as a payload.
	 */
	private Message<?> fromMessageToMessage(Message<?> incomingMessage, Class<?> targetType) {
		if (logger.isDebugEnabled()) {
			logger.debug("Converting message '" + incomingMessage + "' with payload of type '" + incomingMessage.getPayload().getClass().getName()
					+ "' to message with payload of type '" + targetType.getName()
					+ "' for invocation of " + functionRegistration.getNames());
		}
		return MessageBuilder.withPayload(this.fromMessageToValue(incomingMessage, targetType))
				.copyHeaders(incomingMessage.getHeaders()).build();
	}


}
