/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.cloud.function.serializer.jdk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.function.Function;

import org.springframework.cloud.function.compiler.FunctionCompiler;
import org.springframework.cloud.function.compiler.java.SimpleClassLoader;
import org.springframework.cloud.function.serializer.FunctionSerializer;

/**
 * @author Mark Fisher
 */
public class JdkFunctionSerializer implements FunctionSerializer {

	@Override
	public <T, R> byte[] serialize(Function<T, R> function) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(function);
			oos.close();
			return baos.toByteArray();
		}
		catch (IOException e) {
			throw new IllegalArgumentException("failed to serialize function", e);
		}
	}

	@Override
	public <T, R> Function<T, R> deserialize(byte[] functionBytes, byte[] factoryBytes) {
		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(functionBytes);
			ObjectInputStream ois = new GeneratedFunctionFactoryAwareObjectInputStream(bais, factoryBytes);
			Object o = ois.readObject();
			ois.close();
			return (Function<T, R>) o;
		}
		catch (Exception e) {
			throw new IllegalArgumentException("failed to deserialize function", e);
		}
	}

	private static class GeneratedFunctionFactoryAwareObjectInputStream extends ObjectInputStream {

		private final byte[] functionFactoryBytes;

		public GeneratedFunctionFactoryAwareObjectInputStream(InputStream in, byte[] functionFactoryBytes) throws IOException {
			super(in);
			this.functionFactoryBytes = functionFactoryBytes;
		}

		@Override
		protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
			if (FunctionCompiler.GENERATED_FUNCTION_FACTORY_CLASS_NAME.equals(desc.getName())) {
				SimpleClassLoader classLoader = new SimpleClassLoader(this.getClass().getClassLoader());
				try {
					classLoader.defineClass(desc.getName(), this.functionFactoryBytes);
					return classLoader.loadClass(desc.getName());
				}
				finally {
					classLoader.close();
				}
			}
			return super.resolveClass(desc);
		}
	}
}
