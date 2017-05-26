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

package org.springframework.cloud.function.compiler;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.cloud.function.compiler.java.CompilationResult;
import org.springframework.util.ReflectionUtils;

/**
 * @author Mark Fisher
 */
public class CompiledFunctionFactory<T> implements CompilationResultFactory<T> {

	private final T result;

	private final byte[] generatedClassBytes;

	private String inputType;

	private String outputType;

	private Method method;

	public CompiledFunctionFactory(String className,
			CompilationResult compilationResult) {
		List<Class<?>> clazzes = compilationResult.getCompiledClasses();
		T result = null;
		Method method = null;
		for (Class<?> clazz : clazzes) {
			if (clazz.getName().equals(className)) {
				try {
					@SuppressWarnings("unchecked")
					CompilationResultFactory<T> factory = (CompilationResultFactory<T>) clazz
							.newInstance();
					result = factory.getResult();
					method = findFactoryMethod(clazz);
				}
				catch (Exception e) {
					throw new IllegalArgumentException(
							"Unexpected problem during retrieval of Function from compiled class",
							e);
				}
			}
		}
		if (result == null) {
			throw new IllegalArgumentException("Failed to extract compilation result.");
		}
		this.result = result;
		this.method = method;
		this.generatedClassBytes = compilationResult.getClassBytes(className);
	}

	private Method findFactoryMethod(Class<?> clazz) {
		AtomicReference<Method> method = new AtomicReference<>();
		ReflectionUtils.doWithLocalMethods(clazz, m -> {
			if (m.getName().equals("getResult")
					&& m.getReturnType().getName().startsWith("java.util.function")) {
				method.set(m);
			}
		});
		return method.get();
	}

	public T getResult() {
		return result;
	}

	public Method getFactoryMethod() {
		return method;
	}

	public String getInputType() {
		return inputType;
	}

	public void setInputType(String inputType) {
		this.inputType = inputType;
	}

	public String getOutputType() {
		return outputType;
	}

	public void setOutputType(String outputType) {
		this.outputType = outputType;
	}

	public byte[] getGeneratedClassBytes() {
		return generatedClassBytes;
	}
}
