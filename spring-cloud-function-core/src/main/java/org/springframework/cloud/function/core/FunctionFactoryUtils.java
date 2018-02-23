/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.function.core;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.reactivestreams.Publisher;

import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

import reactor.core.publisher.Flux;

/**
 * <p>
 * Miscellaneous utility operations to interrogate functional components (beans)
 * configured in BeanFactory.
 * </p>
 * <p>
 * It is important to understand that it is not a general purpose utility to interrogate
 * "any" functional component. Certain operations may/will not work as expected due to
 * java type erasure. While BeanFactory is not the requirement, this utility is targeting
 * only the components defined in such way where they could be configured beans within
 * BeanFactory.
 * </p>
 * It is primarily used internally by the framework.
 *
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 */
public abstract class FunctionFactoryUtils {

	private static final String FLUX_CLASS_NAME = Flux.class.getName();
	private static final String PUBLISHER_CLASS_NAME = Publisher.class.getName();

	private FunctionFactoryUtils() {
	}

	public static boolean isFluxConsumer(Consumer<?> consumer) {
		return consumer instanceof FunctionFactoryMetadata
				? isFluxConsumer(
						((FunctionFactoryMetadata<?>) consumer).getFactoryMethod())
				: isFlux(1, getParameterizedTypeNames(consumer, Consumer.class));
	}

	public static boolean isFluxConsumer(Method method) {
		return isFlux(1, getParameterizedTypeNamesForMethod(method, Consumer.class));
	}

	public static boolean isFluxSupplier(Supplier<?> supplier) {
		return supplier instanceof FunctionFactoryMetadata
				? isFluxSupplier(
						((FunctionFactoryMetadata<?>) supplier).getFactoryMethod())
				: isFlux(1, getParameterizedTypeNames(supplier, Supplier.class));
	}

	public static boolean isFluxSupplier(Method method) {
		return isFlux(1, getParameterizedTypeNamesForMethod(method, Supplier.class));
	}

	public static boolean isFluxFunction(Function<?, ?> function) {
		return function instanceof FunctionFactoryMetadata
				? isFluxFunction(
						((FunctionFactoryMetadata<?>) function).getFactoryMethod())
				: isFlux(1, getParameterizedTypeNames(function, Function.class));
	}

	public static boolean isFluxFunction(Method method) {
		return isFlux(2, getParameterizedTypeNamesForMethod(method, Function.class));
	}

	private static String[] getParameterizedTypeNamesForMethod(Method method,
			Class<?> interfaceClass) {
		String[] types = retrieveTypes(method.getGenericReturnType(), interfaceClass);
		return types == null ? new String[0] : types;
	}

	private static String[] getParameterizedTypeNames(Object source,
			Class<?> interfaceClass) {
		return Stream.of(source.getClass().getGenericInterfaces())
				.map(gi -> retrieveTypes(gi, interfaceClass)).filter(s -> s != null)
				.findFirst().orElse(getSerializedLambdaParameterizedTypeNames(source));
	}

	private static String[] retrieveTypes(Type genericInterface,
			Class<?> interfaceClass) {
		if ((genericInterface instanceof ParameterizedType) && interfaceClass
				.getTypeName().equals(((ParameterizedType) genericInterface).getRawType()
						.getTypeName())) {
			ParameterizedType type = (ParameterizedType) genericInterface;
			Type[] args = type.getActualTypeArguments();
			if (args != null) {
				return Stream.of(args).map(arg -> arg.getTypeName())
						.toArray(String[]::new);
			}
		}
		return null;
	}

	private static String[] getSerializedLambdaParameterizedTypeNames(Object source) {
		Method method = ReflectionUtils.findMethod(source.getClass(), "writeReplace");
		if (method == null) {
			return null;
		}
		ReflectionUtils.makeAccessible(method);
		SerializedLambda serializedLambda = (SerializedLambda) ReflectionUtils
				.invokeMethod(method, source);
		String signature = serializedLambda.getImplMethodSignature().replaceAll("[()]",
				"");

		List<String> typeNames = Stream.of(signature.split(";"))
				.map(t -> t.substring(1).replace('/', '.')).collect(Collectors.toList());

		return typeNames.toArray(new String[typeNames.size()]);
	}

	private static boolean isFlux(int length, String... types) {
		return !ObjectUtils.isEmpty(types) && types.length == length
				&& Stream.of(types).allMatch(type -> type.startsWith(FLUX_CLASS_NAME)
						|| type.startsWith(PUBLISHER_CLASS_NAME));
	}
}
