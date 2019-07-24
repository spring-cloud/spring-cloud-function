/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.cloud.function.context;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import reactor.core.publisher.Flux;


import org.springframework.core.ResolvableType;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.messaging.Message;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 *
 */
public class FunctionType {

	/**
	 * Unclassified function types.
	 */
	public static FunctionType UNCLASSIFIED = new FunctionType(ResolvableType
			.forClassWithGenerics(Function.class, Object.class, Object.class).getType());

	private static List<WrapperDetector> transformers;

	private Type type;

	private Class<?> inputType;

	private Class<?> outputType;

	private Class<?> inputWrapper;

	private Class<?> outputWrapper;

	private boolean message;

	public FunctionType(Type type) {
		this.type = functionType(type);
		this.inputWrapper = findType(ParamType.INPUT_WRAPPER);
		this.outputWrapper = findType(ParamType.OUTPUT_WRAPPER);
		this.inputType = findType(ParamType.INPUT);
		this.outputType = findType(ParamType.OUTPUT);
		this.message = messageType();
		resetType();
	}

	/*
	 * Experimental for now. Used (reflectively) in FunctionCreatorConfiguration to effectively
	 * map an existing FunctionType created by one class loader to another.
	 */
	@SuppressWarnings("unused") // it is used
	private FunctionType(Object functionType) throws Exception {
		Field[] fields = functionType.getClass().getDeclaredFields();
		for (Field field : fields) {
			if (!Modifier.isStatic(field.getModifiers())) {
				field.setAccessible(true);
				Field thisField = ReflectionUtils.findField(this.getClass(), field.getName());
				thisField.setAccessible(true);
				thisField.set(this, field.get(functionType));
			}
		}
	}

	public static boolean isWrapper(Type type) {
		if (type instanceof ParameterizedType) {
			type = ((ParameterizedType) type).getRawType();
		}
		if (transformers == null) {
			transformers = new ArrayList<>();
			transformers.addAll(
					SpringFactoriesLoader.loadFactories(WrapperDetector.class, null));
		}
		for (WrapperDetector transformer : transformers) {
			if (transformer.isWrapper(type)) {
				return true;
			}
		}
		return false;
	}

	public static FunctionType of(Type function) {
		FunctionType ft = new FunctionType(function);
		if (!ft.isWrapper() && !(ft.type instanceof ParameterizedType)) {
			Type[] genericInterfaces = ((Class<?>) function).getGenericInterfaces();
			if (!ObjectUtils.isEmpty(genericInterfaces)) {
				ft.type = genericInterfaces[0];
			}
		}
		return ft;
	}

	public static FunctionType from(Class<?> input) {
		return new FunctionType(ResolvableType
				.forClassWithGenerics(Function.class, input, Object.class).getType());
	}

	public static FunctionType supplier(Class<?> input) {
		return new FunctionType(
				ResolvableType.forClassWithGenerics(Supplier.class, input).getType());
	}

	public static FunctionType consumer(Class<?> input) {
		return new FunctionType(
				ResolvableType.forClassWithGenerics(Consumer.class, input).getType());
	}

	public static FunctionType compose(FunctionType input, FunctionType output) {
		ResolvableType inputGeneric = input(input);
		ResolvableType outputGeneric = output(output);
		if (!isWrapper(outputGeneric.getType())) {
			ResolvableType inputOutput = output(input);
			if (isWrapper(inputOutput.getType())) {
				outputGeneric = wrap(input,
						extractClass(inputOutput.getType(), ParamType.OUTPUT_WRAPPER),
						extractClass(outputGeneric.getType(), ParamType.OUTPUT));
			}
		}
		return new FunctionType(ResolvableType
				.forClassWithGenerics(Function.class, inputGeneric, outputGeneric)
				.getType());
	}

	public Type getType() {
		return this.type;
	}

	public Class<?> getInputWrapper() {
		return this.inputWrapper;
	}

	public Class<?> getOutputWrapper() {
		return this.outputWrapper;
	}

	public Class<?> getInputType() {
		return this.inputType;
	}

	public Class<?> getOutputType() {
		return this.outputType;
	}

	public boolean isMessage() {
		return this.message;
	}

	public boolean isWrapper() {
		return isWrapper(getInputWrapper()) || isWrapper(getOutputWrapper());
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

	public FunctionType wrap(Class<?> input, Class<?> output) {
		if (!isWrapper(input) && !isWrapper(output)) {
			return this;
		}
		else if (isWrapper(input) && isWrapper(output)) {
			if (input.isAssignableFrom(getInputWrapper())
					&& output.isAssignableFrom(getOutputWrapper())) {
				return this;
			}
			return new FunctionType(ResolvableType.forClassWithGenerics(Function.class,
					wrapper(input, getInputType()), wrapper(output, getOutputType()))
					.getType());
		}
		else {
			throw new IllegalArgumentException("Both wrapper types must be wrappers in ("
					+ input + ", " + output + ")");
		}
	}

	public FunctionType wrap(Class<?> wrapper) {
		return wrap(wrapper, wrapper);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((this.inputType == null) ? 0 : this.inputType.toString().hashCode());
		result = prime * result + ((this.inputWrapper == null) ? 0
				: this.inputWrapper.toString().hashCode());
		result = prime * result + (this.message ? 1231 : 1237);
		result = prime * result
				+ ((this.outputType == null) ? 0 : this.outputType.toString().hashCode());
		result = prime * result + ((this.outputWrapper == null) ? 0
				: this.outputWrapper.toString().hashCode());
		return result;
	}

	public String toString() {
		if (this.inputType == Void.class) {
			return this.type.toString() + ", which is effectively a Supplier<"
					+ this.outputType + ">";
		}
		else if (this.outputType == Void.class) {
			return this.type.toString() + ", which is effectively a Consumer<"
					+ this.inputType + ">";
		}
		return this.type.toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		FunctionType other = (FunctionType) obj;
		if (this.inputType == null) {
			if (other.inputType != null) {
				return false;
			}
		}
		else if (!this.inputType.toString().equals(other.inputType.toString())) {
			return false;
		}
		if (this.inputWrapper == null) {
			if (other.inputWrapper != null) {
				return false;
			}
		}
		else if (!this.inputWrapper.toString().equals(other.inputWrapper.toString())) {
			return false;
		}
		if (this.message != other.message) {
			return false;
		}
		if (this.outputType == null) {
			if (other.outputType != null) {
				return false;
			}
		}
		else if (!this.outputType.toString().equals(other.outputType.toString())) {
			return false;
		}
		if (this.outputWrapper == null) {
			if (other.outputWrapper != null) {
				return false;
			}
		}
		else if (!this.outputWrapper.toString().equals(other.outputWrapper.toString())) {
			return false;
		}
		return true;
	}

	private static ResolvableType wrap(FunctionType input, Class<?> wrapper,
			Class<?> type) {
		return input.isMessage() ? wrap(wrapper, message(type))
				: ResolvableType.forClassWithGenerics(wrapper, type);
	}

	private static ResolvableType wrap(Class<?> wrapper, ResolvableType type) {
		return ResolvableType.forClassWithGenerics(wrapper, type);
	}

	private static ResolvableType message(Class<?> type) {
		return ResolvableType.forClassWithGenerics(Message.class, type);
	}

	private static ResolvableType input(FunctionType type) {
		return type.input(type.getInputType());
	}

	private static ResolvableType output(FunctionType type) {
		return type.output(type.getOutputType());
	}

	private static Class<?> extractClass(Type param, ParamType paramType) {
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

	private ResolvableType wrapper(Class<?> wrapper, Class<?> type) {
		return wrap(this, wrapper, type);
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
		if (Supplier.class.isAssignableFrom(extractClass(this.type, null))) {
			if (paramType.isInput()) {
				return Void.class;
			}
		}
		boolean found = false;
		while (!found && type instanceof Class && type != Object.class) {
			Class<?> clz = (Class<?>) type;
			for (Type iface : clz.getGenericInterfaces()) {
				if (iface.getTypeName().startsWith("java.util.function")) {
					type = iface;
					found = true;
					break;
				}
			}
			if (!found) {
				type = clz.getSuperclass();
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

	private void resetType() {
		if (!this.type.getTypeName().contains("EnhancerBySpringCGLIB")) {
			return;
		}
		Type type = this.type;

		boolean found = false;
		while (!found && type instanceof Class && type != Object.class) {
			Class<?> clz = (Class<?>) type;
			for (Type iface : clz.getGenericInterfaces()) {
				if (iface.getTypeName().startsWith("java.util.function")) {
					type = iface;
					found = true;
					break;
				}
			}
			if (!found) {
				type = clz.getSuperclass();
			}
		}
		this.type = type;
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
			if (type != null) {
				Type[] interfaces = ((Class<?>) type).getGenericInterfaces();
				for (Type ifc : interfaces) {
					Type value = extractType(ifc, paramType, index);
					if (value != Object.class) {
						return value;
					}
				}
			}
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

	private Type functionType(Type type) {
		if (Supplier.class.isAssignableFrom(extractClass(type, ParamType.OUTPUT))) {
			Type product = extractType(type, ParamType.OUTPUT, 0);
			Class<?> output = extractClass(product, ParamType.OUTPUT);
			if (output != null) {
				if (FunctionRegistration.class.isAssignableFrom(output)) {
					type = extractType(product, ParamType.OUTPUT, 0);
				}
				else if (Function.class.isAssignableFrom(output)
						|| Supplier.class.isAssignableFrom(output)
						|| Consumer.class.isAssignableFrom(output)) {
					type = product;
				}
			}
		}
		return type;
	}

	private boolean messageType() {
		Class<?> inputType = findType(ParamType.INPUT_INNER_WRAPPER);
		Class<?> outputType = findType(ParamType.OUTPUT_INNER_WRAPPER);
		return inputType.getName().startsWith(Message.class.getName())
				|| Message.class.isAssignableFrom(inputType)
				|| outputType.getName().startsWith(Message.class.getName())
				|| Message.class.isAssignableFrom(outputType);
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
