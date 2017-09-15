/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.compiler.proxy;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.asm.ClassReader;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.function.compiler.CompilationResultFactory;
import org.springframework.cloud.function.compiler.java.SimpleClassLoader;
import org.springframework.cloud.function.core.FunctionFactoryMetadata;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.ReflectionUtils;

/**
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 */
abstract class AbstractByteCodeLoadingProxy<T> implements InitializingBean, FunctionFactoryMetadata<T> {

	private final Resource resource;

	private final SimpleClassLoader classLoader = new SimpleClassLoader(AbstractByteCodeLoadingProxy.class.getClassLoader());

	private T target;

	private Method method;

	AbstractByteCodeLoadingProxy(Resource resource) {
		this.resource = resource;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void afterPropertiesSet() throws Exception {
		byte[] bytes = FileCopyUtils.copyToByteArray(this.resource.getInputStream());
		String className = new ClassReader(bytes).getClassName().replace("/", ".");
		Class<?> factoryClass = this.classLoader.defineClass(className, bytes);
		try {
			this.target = ((CompilationResultFactory<T>) factoryClass.newInstance()).getResult();
			this.method = findFactoryMethod(factoryClass);
		}
		catch (InstantiationException | IllegalAccessException e) {
			throw new IllegalArgumentException("failed to load Function byte code", e);
		}
	}

	@Override
	public final T getTarget() {
		return this.target;
	}

	@Override
	public Method getFactoryMethod() {
		return this.method;
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
}
