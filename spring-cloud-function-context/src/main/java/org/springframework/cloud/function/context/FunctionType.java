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
package org.springframework.cloud.function.context;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;

import org.springframework.core.ResolvableType;
import org.springframework.messaging.Message;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Dave Syer
 *
 */
public class FunctionType {

	public static FunctionType UNCLASSIFIED = new FunctionType(ResolvableType
			.forClassWithGenerics(Function.class, Object.class, Object.class).getType());

	private Type type;

	public FunctionType(Type type) {
		this.type = type;
	}

	public Type getType() {
		return type;
	}

	public Class<?> getInputWrapper() {
		return findType(ParamType.INPUT_WRAPPER);
	}

	public Class<?> getOutputWrapper() {
		return findType(ParamType.OUTPUT_WRAPPER);
	}

	public Class<?> getInputType() {
		return findType(ParamType.INPUT);
	}

	public Class<?> getOutputType() {
		return findType(ParamType.OUTPUT);
	}

	public boolean isMessage() {
		Class<?> inputType = findType(ParamType.INPUT_INNER_WRAPPER);
		Class<?> outputType = findType(ParamType.OUTPUT_INNER_WRAPPER);
		return inputType.getName().startsWith(Message.class.getName())
				|| Message.class.isAssignableFrom(inputType)
				|| outputType.getName().startsWith(Message.class.getName())
				|| Message.class.isAssignableFrom(outputType);
	}

	public boolean isWrapper() {
		return isWrapper(getInputWrapper()) || isWrapper(getOutputWrapper());
	}

	public static boolean isWrapper(Type type) {
		return Publisher.class.equals(type) || Flux.class.equals(type)
				|| Mono.class.equals(type) || Optional.class.equals(type);
	}

	public static FunctionType of(Type function) {
		return new FunctionType(function);
	}

	public static FunctionType from(Class<?> input) {
		return new FunctionType(ResolvableType
				.forClassWithGenerics(Function.class, input, Object.class).getType());
	}

	public FunctionType to(Class<?> output) {
		ResolvableType inputGeneric = input(this);
		ResolvableType outputGeneric = output(output);
		return new FunctionType(ResolvableType
				.forClassWithGenerics(Function.class, inputGeneric, outputGeneric)
				.getType());
	}

	public FunctionType message() {
		if (isMessage()) {
			return this;
		}
		ResolvableType inputGeneric = message(getInputType());
		ResolvableType outputGeneric = message(getOutputType());
		if (isWrapper(getInputWrapper())) {
			inputGeneric = ResolvableType.forClassWithGenerics(getInputWrapper(),
					inputGeneric);
			outputGeneric = ResolvableType.forClassWithGenerics(getInputWrapper(),
					outputGeneric);
		}
		return new FunctionType(ResolvableType
				.forClassWithGenerics(Function.class, inputGeneric, outputGeneric)
				.getType());
	}

	public FunctionType wrap(Class<?> wrapper) {
		if (wrapper.isAssignableFrom(getInputWrapper())) {
			return this;
		}
		return new FunctionType(ResolvableType.forClassWithGenerics(Function.class,
				wrap(wrapper, getInputType()), wrap(wrapper, getOutputType())).getType());
	}

	public static FunctionType compose(FunctionType input, FunctionType output) {
		ResolvableType inputGeneric = input(input);
		ResolvableType outputGeneric = output(output);
		return new FunctionType(ResolvableType
				.forClassWithGenerics(Function.class, inputGeneric, outputGeneric)
				.getType());
	}

	private ResolvableType wrap(Class<?> wrapper, Class<?> type) {
		return isMessage() ? wrap(wrapper, message(type))
				: ResolvableType.forClassWithGenerics(wrapper, type);
	}

	private ResolvableType wrap(Class<?> wrapper, ResolvableType type) {
		return ResolvableType.forClassWithGenerics(wrapper, type);
	}

	private ResolvableType message(Class<?> type) {
		return ResolvableType.forClassWithGenerics(Message.class, type);
	}

	private static ResolvableType input(FunctionType type) {
		return type.input(type.getInputType());
	}

	private static ResolvableType output(FunctionType type) {
		return type.output(type.getOutputType());
	}

	private ResolvableType output(Class<?> type) {
		ResolvableType generic;
		ResolvableType raw = ResolvableType.forClass(type);
		if (isMessage()) {
			raw = ResolvableType.forClassWithGenerics(Message.class, raw);
		}
		if (FunctionType.isWrapper(getOutputWrapper())) {
			generic = ResolvableType.forClassWithGenerics(getOutputWrapper(), raw);
		}
		else {
			generic = raw;
		}
		return generic;
	}

	private ResolvableType input(Class<?> type) {
		ResolvableType generic;
		ResolvableType raw = ResolvableType.forClass(type);
		if (isMessage()) {
			raw = ResolvableType.forClassWithGenerics(Message.class, raw);
		}
		if (FunctionType.isWrapper(getInputWrapper())) {
			generic = ResolvableType.forClassWithGenerics(getInputWrapper(), raw);
		}
		else {
			generic = raw;
		}
		return generic;
	}

	private Class<?> findType(ParamType paramType) {
		int index = paramType.isOutput() ? 1 : 0;
		Type type = this.type;
		if (type instanceof Class) {
			for (Type iface : ((Class<?>) type).getGenericInterfaces()) {
				if (iface.getTypeName().startsWith("java.util.function")) {
					type = iface;
					break;
				}
			}
		}
		Type param = extractType(type, paramType, index);
		if (param != null) {
			Class<?> result = extractClass(param, paramType);
			if (result != null) {
				return result;
			}
		}
		return Object.class;
	}

	private Class<?> extractClass(Type param, ParamType paramType) {
		if (param instanceof ParameterizedType) {
			ParameterizedType concrete = (ParameterizedType) param;
			param = concrete.getRawType();
		}
		if (param == null) {
			// Last ditch attempt to guess: Flux<String>
			if (paramType.isWrapper()) {
				param = Flux.class;
			}
			else {
				param = String.class;
			}
		}
		Class<?> result = param instanceof Class ? (Class<?>) param : null;
		// TODO: cache result
		return result;
	}

	private Type extractType(Type type, ParamType paramType, int index) {
		Type param;
		if (type instanceof ParameterizedType) {
			ParameterizedType parameterizedType = (ParameterizedType) type;
			if (parameterizedType.getActualTypeArguments().length == 1) {
				if (isVoid(parameterizedType, paramType)) {
					return Void.class;
				}
				// There's only one
				index = 0;
			}
			Type typeArgumentAtIndex = parameterizedType.getActualTypeArguments()[index];
			if (typeArgumentAtIndex instanceof ParameterizedType
					&& !paramType.isWrapper()) {
				if (FunctionType.isWrapper(
						((ParameterizedType) typeArgumentAtIndex).getRawType())) {
					param = ((ParameterizedType) typeArgumentAtIndex)
							.getActualTypeArguments()[0];
					param = extractNestedType(paramType, param);
				}
				else {
					param = extractNestedType(paramType, typeArgumentAtIndex);
				}
			}
			else {
				param = extractNestedType(paramType, typeArgumentAtIndex);
			}
		}
		else {
			param = Object.class;
		}
		return param;
	}

	private boolean isVoid(ParameterizedType parameterizedType, ParamType paramType) {
		Class<?> rawType = extractClass(parameterizedType.getRawType(), paramType);
		if (Consumer.class.isAssignableFrom(rawType) && paramType.isOutput()) {
			return true;
		}
		if (Supplier.class.isAssignableFrom(rawType) && paramType.isInput()) {
			return true;
		}
		return false;
	}

	private Type extractNestedType(ParamType paramType, Type param) {
		if (!paramType.isInnerWrapper() && param instanceof ParameterizedType) {
			if (((ParameterizedType) param).getRawType().getTypeName()
					.startsWith(Message.class.getName())) {
				param = ((ParameterizedType) param).getActualTypeArguments()[0];
			}
		}
		return param;
	}

	enum ParamType {
		INPUT, OUTPUT, INPUT_WRAPPER, OUTPUT_WRAPPER, INPUT_INNER_WRAPPER, OUTPUT_INNER_WRAPPER;

		public boolean isOutput() {
			return this == OUTPUT || this == OUTPUT_WRAPPER
					|| this == OUTPUT_INNER_WRAPPER;
		}

		public boolean isInput() {
			return this == INPUT || this == INPUT_WRAPPER || this == INPUT_INNER_WRAPPER;
		}

		public boolean isWrapper() {
			return this == OUTPUT_WRAPPER || this == INPUT_WRAPPER;
		}

		public boolean isInnerWrapper() {
			return this == OUTPUT_INNER_WRAPPER || this == INPUT_INNER_WRAPPER;
		}
	}

}
