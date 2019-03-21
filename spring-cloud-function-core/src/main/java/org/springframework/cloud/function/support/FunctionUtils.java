/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.function.support;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

import reactor.core.publisher.Flux;

/**
 * @author Mark Fisher
 */
public abstract class FunctionUtils {

	private static final String FLUX_CLASS_NAME = Flux.class.getName();

	private static final Pattern FUNCTION_METHOD_SIGNATURE_TYPE_PATTERN = Pattern.compile("^\\(L(.*?);\\)L(.*?);$");

	private FunctionUtils() {}

	public static boolean isFluxSupplier(Supplier<?> supplier) {
		String[] types = getParameterizedTypeNames(supplier, Supplier.class);
		if (ObjectUtils.isEmpty(types)) {
			return true;
		}
		return (types[0].startsWith(FLUX_CLASS_NAME));
	}

	@SuppressWarnings("rawtypes")
	public static boolean isFluxFunction(Function<?, ?> function) {
		if (function instanceof FunctionProxy) {
			return ((FunctionProxy) function).isFluxFunction();
		}
		String[] types = getParameterizedTypeNames(function, Function.class);
		if (ObjectUtils.isEmpty(types) || types.length != 2) {
			return true;
		}
		return (types[0].startsWith(FLUX_CLASS_NAME) && types[1].startsWith(FLUX_CLASS_NAME));
	}

	private static String[] getParameterizedTypeNames(Object source, Class<?> interfaceClass) {
		Type[] genericInterfaces = source.getClass().getGenericInterfaces();
		for (Type genericInterface : genericInterfaces) {
			if ((genericInterface instanceof ParameterizedType)
					&& interfaceClass.getTypeName().equals(((ParameterizedType) genericInterface).getRawType().getTypeName())) {
				ParameterizedType type = (ParameterizedType) genericInterface;
				Type[] args = type.getActualTypeArguments();
				if (args != null) {
					String[] typeNames = new String[args.length];
					for (int i = 0; i < args.length; i++) {
						typeNames[i] = args[i].getTypeName();
					}
					return typeNames;
				}
			}
		}
		return getSerializedLambdaParameterizedTypeNames(source);
	}

	private static String[] getSerializedLambdaParameterizedTypeNames(Object source) {
		Method method = ReflectionUtils.findMethod(source.getClass(), "writeReplace");
		if (method == null) {
			return null;
		}
		ReflectionUtils.makeAccessible(method);
		SerializedLambda serializedLambda = (SerializedLambda) ReflectionUtils.invokeMethod(method, source);
		String signature = serializedLambda.getImplMethodSignature();
		Matcher matcher = FUNCTION_METHOD_SIGNATURE_TYPE_PATTERN.matcher(signature);
		if (!matcher.matches()) {
			return new String[0];
		}
		String[] typeNames = new String[matcher.groupCount()];
		for (int i = 0; i < matcher.groupCount(); i++) {
			typeNames[i] = matcher.group(i + 1).replace('/', '.');
		}
		return typeNames;
	}
}
