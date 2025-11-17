/*
 * Copyright 2019-present the original author or authors.
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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.Ssl.ServerNameSslBundle;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.config.FunctionContextUtils;
import org.springframework.cloud.function.context.message.MessageUtils;

/**
 * Ensure that Function/Consumer input types are reflectively available.
 *
 * @author Oleg Zhurakousky
 */
public class FunctionTypeProcessor implements BeanFactoryInitializationAotProcessor {

	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
		Set<Class<?>> typeHints = new HashSet<>();

		String[] names = beanFactory.getBeanDefinitionNames();
		for (int i = 0; i < names.length; i++) {
			String beanName = names[i];
			Class<?> beanClass = beanFactory.getType(beanName);
			if (this.isFunction(beanClass)) {
				Type functionType = FunctionTypeUtils.discoverFunctionTypeFromClass(beanClass);

				if (!(functionType instanceof ParameterizedType)) {
					functionType = FunctionContextUtils.findType(beanFactory, beanName);
				}
				this.registerAllGenericTypes((ParameterizedType) functionType, typeHints);
			}
		}
		return new ReflectiveProcessorBeanFactoryInitializationAotContribution(typeHints.toArray(Class[]::new));
	}

	private void registerAllGenericTypes(ParameterizedType type, Set<Class<?>> typeHints) {
		Type[] types = type.getActualTypeArguments();
		for (int i = 0; i < types.length; i++) {
			Type functionParameterType = types[i];
			String name = functionParameterType.getTypeName();
			if (!isCoreJavaType(name)) {
				typeHints.add(FunctionTypeUtils.getRawType(functionParameterType));
			}
			if (functionParameterType instanceof ParameterizedType) {
				this.registerAllGenericTypes((ParameterizedType) functionParameterType, typeHints);
			}
		}
	}

	private boolean isCoreJavaType(String className) {
		return className.startsWith("java.") || className.startsWith("javax.");
	}

	private boolean isFunction(Class<?> beanType) {
		return Function.class.isAssignableFrom(beanType) || Consumer.class.isAssignableFrom(beanType)
				|| Supplier.class.isAssignableFrom(beanType);
	}

	private static final class ReflectiveProcessorBeanFactoryInitializationAotContribution
			implements BeanFactoryInitializationAotContribution {

		private final Class<?>[] typeHints;

		private ReflectiveProcessorBeanFactoryInitializationAotContribution(Class<?>[] typeHints) {
			this.typeHints = typeHints;
		}

		@Override
		public void applyTo(GenerationContext generationContext,
				BeanFactoryInitializationCode beanFactoryInitializationCode) {
			RuntimeHints runtimeHints = generationContext.getRuntimeHints();
			for (int i = 0; i < typeHints.length; i++) {
				runtimeHints.reflection()
					.registerType(typeHints[i], MemberCategory.PUBLIC_FIELDS, MemberCategory.INVOKE_DECLARED_METHODS,
							MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
			}

			// known static types
			runtimeHints.reflection()
				.registerType(MessageUtils.MessageStructureWithCaseInsensitiveHeaderKeys.class,
						MemberCategory.INVOKE_PUBLIC_METHODS);

			// temporary due to bug in boot
			runtimeHints.reflection().registerType(Ssl.class, MemberCategory.INVOKE_PUBLIC_METHODS);
			runtimeHints.reflection().registerType(ServerNameSslBundle.class, MemberCategory.INVOKE_PUBLIC_METHODS);
		}

	}

}
