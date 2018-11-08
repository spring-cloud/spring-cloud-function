/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.cloud.function.context.config;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.MethodMetadata;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

/**
 * Configuration class which defines the required infrastructure to bootstrap Kotlin
 * lambdas as invocable functions within the context of the framework.
 *
 * @author Oleg Zhurakousky
 *
 * @since 2.0
 */
@Configuration
@ConditionalOnClass(name = "kotlin.jvm.functions.Function1")
class KotlinLambdaToFunctionAutoConfiguration {

	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * Will transform all discovered Kotlin's Function1 and Function0 lambdas to java
	 * Supplier, Function and Consumer, retaining the original Kotlin type
	 * characteristics. In other words the resulting bean could be cast to both java and
	 * kotlin types (i.e., java Function<I,O> vs. kotlin Function1<I,O>)
	 */
	@Bean
	public BeanFactoryPostProcessor kotlinToFunctionTransformer() {
		return new BeanFactoryPostProcessor() {

			@Override
			public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

				String[] beanDefinitionNames = beanFactory.getBeanDefinitionNames();
				for (String beanDefinitionName : beanDefinitionNames) {
					BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanDefinitionName);
					Object source = beanDefinition.getSource();
					if (source instanceof MethodMetadata) {
						String returnTypeName = ((MethodMetadata)source).getReturnTypeName();
						if (returnTypeName.startsWith("kotlin.jvm.functions.Function")) {
							FunctionType functionType = new FunctionType(FunctionContextUtils.findType(beanDefinitionName, beanFactory));
							if (returnTypeName.equals("kotlin.jvm.functions.Function1")) {
								if (Unit.class.isAssignableFrom(functionType.getOutputType())) {
									logger.debug("Transforming Kotlin lambda " + beanDefinitionName + " to java Consumer");
									this.register(beanDefinitionName, beanDefinition, KotlinConsumer.class, (BeanDefinitionRegistry) beanFactory);
								}
								else {
									logger.debug("Transforming Kotlin lambda " + beanDefinitionName + " to java Function");
									this.register(beanDefinitionName, beanDefinition, KotlinFunction.class, (BeanDefinitionRegistry) beanFactory);
								}
							}
							else  {
								logger.debug("Transforming Kotlin lambda " + beanDefinitionName + " to java Supplier");
								this.register(beanDefinitionName, beanDefinition, KotlinSupplier.class, (BeanDefinitionRegistry) beanFactory);
							}
						}
					}
				}
			}

			private void register(String originalName, BeanDefinition originalDefinition,  Class<?> clazz, BeanDefinitionRegistry registry) {
				RootBeanDefinition cbd = new RootBeanDefinition(clazz);
				ConstructorArgumentValues ca = new ConstructorArgumentValues();
				ca.addGenericArgumentValue(originalDefinition);
				cbd.setConstructorArgumentValues(ca);
				registry.removeBeanDefinition(originalName);
				registry.registerBeanDefinition(originalName, cbd);
			}
		};
	}

	/**
	 * Wrapper for Kotlin lambda to be represented as both Java Function<I,O> as well as
	 * Kotlin's Function1<I,O>
	 */
	private static class KotlinFunction<I, O> implements Function<I, O>, Function1<I, O> {

		private final Function1<I, O> kotlinLambda;

		private KotlinFunction(Function1<I, O> kotlinLambda) {
			this.kotlinLambda = kotlinLambda;
		}

		@Override
		public O apply(I i) {
			return this.kotlinLambda.invoke(i);
		}

		@Override
		public O invoke(I i) {
			return this.apply(i);
		}
	}

	/**
	 * Wrapper for Kotlin lambda to be represented as both Java Consumer<I> as well as
	 * Kotlin's Function1<I,Unit>
	 */
	private static class KotlinConsumer<I, U> implements Consumer<I>, Function1<I, U> {

		private final Function1<I, U> kotlinLambda;

		private KotlinConsumer(Function1<I, U> kotlinLambda) {
			this.kotlinLambda = kotlinLambda;
		}

		@Override
		public U invoke(I i) {
			return this.kotlinLambda.invoke(i);
		}

		@Override
		public void accept(I i) {
			this.kotlinLambda.invoke(i);
		}
	}

	/**
	 * Wrapper for Kotlin lambda to be represented as both Java Supplier<O> as well as
	 * Kotlin's Function0<O>
	 */
	private static class KotlinSupplier<O> implements Supplier<O>, Function0<O> {

		private final Function0<O> kotlinLambda;

		private KotlinSupplier(Function0<O> kotlinLambda) {
			this.kotlinLambda = kotlinLambda;
		}

		@Override
		public O get() {
			return this.invoke();
		}

		@Override
		public O invoke() {
			return this.kotlinLambda.invoke();
		}
	}

}
