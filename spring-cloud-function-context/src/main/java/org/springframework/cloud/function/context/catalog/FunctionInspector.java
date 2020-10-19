/*
 * Copyright 2012-2020 the original author or authors.
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

package org.springframework.cloud.function.context.catalog;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

import net.jodah.typetools.TypeResolver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 *
 * @deprecated since 3.1 no longer used by the framework
 */
@Deprecated
public interface FunctionInspector {

	FunctionRegistration<?> getRegistration(Object function);

	/**
	 *
	 * @deprecated since 3.1 no longer used by the framework
	 */
	@Deprecated
	default boolean isMessage(Object function) {
		if (function == null) {
			return false;
		}

		return ((FunctionInvocationWrapper) function).isInputTypeMessage();
	}

	/**
	 *
	 * @deprecated since 3.1 no longer used by the framework
	 */
	@Deprecated
	default Class<?> getInputType(Object function) {
		if (function == null) {
			return Object.class;
		}
		Type type = ((FunctionInvocationWrapper) function).getInputType();
		Class<?> inputType;
		if (type instanceof ParameterizedType) {
			if (function != null && (((FunctionInvocationWrapper) function).isInputTypePublisher() || ((FunctionInvocationWrapper) function).isInputTypeMessage())) {
				inputType = TypeResolver.resolveRawClass(FunctionTypeUtils.getImmediateGenericType(type, 0), null);
			}
			else {
				inputType = ((FunctionInvocationWrapper) function).getRawInputType();
			}
		}
		else {
			inputType = type instanceof TypeVariable || type instanceof WildcardType ? Object.class : (Class<?>) type;
		}
		return inputType;
	}

	/**
	 *
	 * @deprecated since 3.1 no longer used by the framework
	 */
	@Deprecated
	default Class<?> getOutputType(Object function) {
		if (function == null) {
			return Object.class;
		}
		Type type = ((FunctionInvocationWrapper) function).getOutputType();
		Class<?> outputType;
		if (type instanceof ParameterizedType) {
			if (function != null && ((FunctionInvocationWrapper) function).isOutputTypePublisher() || ((FunctionInvocationWrapper) function).isOutputTypeMessage()) {
				outputType = TypeResolver.resolveRawClass(FunctionTypeUtils.getImmediateGenericType(type, 0), null);
			}
			else {
				outputType = ((FunctionInvocationWrapper) function).getRawOutputType();
			}
		}
		else {
			outputType = type instanceof TypeVariable || type instanceof WildcardType ? Object.class : (Class<?>) type;
		}
		return outputType;
	}

	/**
	 *
	 * @deprecated since 3.1 no longer used by the framework
	 */
	@Deprecated
	default Class<?> getInputWrapper(Object function) {
		Class c = function == null ? Object.class : TypeResolver.resolveRawClass(((FunctionInvocationWrapper) function).getInputType(), null);
		if (Flux.class.isAssignableFrom(c)) {
			return c;
		}
		else if (Mono.class.isAssignableFrom(c)) {
			return c;
		}
		else {
			return this.getInputType(function);
		}
	}

	/**
	 *
	 * @deprecated since 3.1 no longer used by the framework
	 */
	@Deprecated
	default Class<?> getOutputWrapper(Object function) {
		Class c  = function == null ? Object.class : TypeResolver.resolveRawClass(((FunctionInvocationWrapper) function).getOutputType(), null);
		if (Flux.class.isAssignableFrom(c)) {
			return c;
		}
		else if (Mono.class.isAssignableFrom(c)) {
			return c;
		}
		else {
			return this.getOutputType(function);
		}
	}

	/**
	 *
	 * @deprecated since 3.1 no longer used by the framework
	 */
	@Deprecated
	default String getName(Object function) {
		if (function == null) {
			return null;
		}
		return ((FunctionInvocationWrapper) function).getFunctionDefinition();
	}

}
