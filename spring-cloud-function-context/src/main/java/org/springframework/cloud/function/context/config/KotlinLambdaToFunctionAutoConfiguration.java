/*
 * Copyright 2012-2021 the original author or authors.
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

package org.springframework.cloud.function.context.config;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.module.kotlin.KotlinModule;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.functions.Function3;
import kotlin.jvm.functions.Function4;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.util.ObjectUtils;

/**
 * Configuration class which defines the required infrastructure to bootstrap Kotlin
 * lambdas as invocable functions within the context of the framework.
 *
 * @author Oleg Zhurakousky
 * @author Adrien Poupard
 * @author Dmitriy Tsypov
 * @since 2.0
 */
@Configuration
@ConditionalOnClass(name = "kotlin.jvm.functions.Function0")
public class KotlinLambdaToFunctionAutoConfiguration {

	protected final Log logger = LogFactory.getLog(getClass());

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnClass(name = {"org.springframework.http.converter.json.Jackson2ObjectMapperBuilder",
			"com.fasterxml.jackson.module.kotlin.KotlinModule"})
	Jackson2ObjectMapperBuilderCustomizer customizer() {
		return new Jackson2ObjectMapperBuilderCustomizer() {
			@Override
			public void customize(Jackson2ObjectMapperBuilder jacksonObjectMapperBuilder) {
				jacksonObjectMapperBuilder.modulesToInstall(KotlinModule.class);
			}
		};
	}

	/**
	 * Will transform all discovered Kotlin's Function lambdas to java
	 * Supplier, Function and Consumer, retaining the original Kotlin type
	 * characteristics.
	 *
	 * @return the bean factory post processor
	 */
	@Bean
	public SmartInitializingSingleton kotlinToFunctionTransformer(ConfigurableListableBeanFactory beanFactory) {
		return new SmartInitializingSingleton() {

			@Override
			public void afterSingletonsInstantiated() {
				String[] beanDefinitionNames = beanFactory.getBeanDefinitionNames();
				for (String beanDefinitionName : beanDefinitionNames) {
					BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanDefinitionName);

					if (beanDefinition instanceof AnnotatedBeanDefinition && ((AnnotatedBeanDefinition) beanDefinition).getFactoryMethodMetadata() != null) {
						String typeName = ((AnnotatedBeanDefinition) beanDefinition).getFactoryMethodMetadata().getReturnTypeName();
						if (typeName.startsWith("kotlin.jvm.functions.Function")) {
							RootBeanDefinition cbd = new RootBeanDefinition(KotlinFunctionWrapper.class);
							ConstructorArgumentValues ca = new ConstructorArgumentValues();
							ca.addGenericArgumentValue(beanDefinition);
							cbd.setConstructorArgumentValues(ca);
							((BeanDefinitionRegistry) beanFactory).registerBeanDefinition(beanDefinitionName + FunctionRegistration.REGISTRATION_NAME_SUFFIX, cbd);
						}
					}
				}
			}

		};
	}


	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static final class KotlinFunctionWrapper implements Function<Object, Object>, Supplier<Object>, Consumer<Object>,
			Function0<Object>, Function1<Object, Object>, Function2<Object, Object, Object>,
			Function3<Object, Object, Object, Object>, Function4<Object, Object, Object, Object, Object>,
			FactoryBean<FunctionRegistration>,
			BeanNameAware,
			BeanFactoryAware {

		private final Object kotlinLambdaTarget;

		private String name;

		private ConfigurableListableBeanFactory beanFactory;

		public KotlinFunctionWrapper(Object kotlinLambdaTarget) {
			this.kotlinLambdaTarget = kotlinLambdaTarget;
		}

		@Override
		public Object apply(Object input) {
			if (ObjectUtils.isEmpty(input)) {
				return this.invoke();
			}
			else if (ObjectUtils.isArray(input)) {
				return null;
			}
			else {
				return this.invoke(input);
			}
		}

		@Override
		public Object invoke(Object arg0, Object arg1, Object arg2, Object arg3) {
			return ((Function4) this.kotlinLambdaTarget).invoke(arg0, arg1, arg2, arg3);
		}

		@Override
		public Object invoke(Object arg0, Object arg1, Object arg2) {
			return ((Function3) this.kotlinLambdaTarget).invoke(arg0, arg1, arg2);
		}

		@Override
		public Object invoke(Object arg0, Object arg1) {
			return ((Function2) this.kotlinLambdaTarget).invoke(arg0, arg1);
		}

		@Override
		public Object invoke(Object arg0) {
			if (CoroutinesUtils.isValidSuspendingFunction(kotlinLambdaTarget, arg0)) {
				return CoroutinesUtils.invokeSuspendingFunction(kotlinLambdaTarget, arg0);
			}
			return ((Function1) this.kotlinLambdaTarget).invoke(arg0);
		}

		@Override
		public Object invoke() {
			if (CoroutinesUtils.isValidSuspendingSupplier(kotlinLambdaTarget)) {
				return CoroutinesUtils.invokeSuspendingSupplier(kotlinLambdaTarget);
			}
			return ((Function0) this.kotlinLambdaTarget).invoke();
		}

		@Override
		public void accept(Object input) {
			if (CoroutinesUtils.isValidSuspendingFunction(kotlinLambdaTarget, input)) {
				CoroutinesUtils.invokeSuspendingConsumer(kotlinLambdaTarget, input);
				return;
			}
			this.apply(input);
		}

		@Override
		public Object get() {
			return this.apply(null);
		}

		@Override
		public FunctionRegistration getObject() throws Exception {
			String name = this.name.endsWith(FunctionRegistration.REGISTRATION_NAME_SUFFIX)
				? this.name.replace(FunctionRegistration.REGISTRATION_NAME_SUFFIX, "")
				: this.name;
			Type functionType = FunctionContextUtils.findType(name, this.beanFactory);
			FunctionRegistration<?> registration = new FunctionRegistration<>(this, name);
			Type[] types = ((ParameterizedType) functionType).getActualTypeArguments();

			if (isValidKotlinSupplier(functionType)) {
				functionType = ResolvableType.forClassWithGenerics(Supplier.class, ResolvableType.forType(types[0]))
					.getType();
			}
			else if (isValidKotlinConsumer(functionType, types)) {
				functionType = ResolvableType.forClassWithGenerics(Consumer.class, ResolvableType.forType(types[0]))
					.getType();
			}
			else if (isValidKotlinFunction(functionType, types)) {
				functionType = ResolvableType.forClassWithGenerics(Function.class, ResolvableType.forType(types[0]),
					ResolvableType.forType(types[1])).getType();
			}
			else if (isValidKotlinSuspendSupplier(functionType, types)) {
				Type continuationReturnType = CoroutinesUtils.getSuspendingFunctionReturnType(types[0]);
				functionType = ResolvableType.forClassWithGenerics(
					Supplier.class,
					ResolvableType.forClassWithGenerics(Flux.class, ResolvableType.forType(continuationReturnType))
				).getType();
			}
			else if (isValidKotlinSuspendFunction(functionType, types)) {
				Type continuationArgType = CoroutinesUtils.getSuspendingFunctionArgType(types[0]);
				Type continuationReturnType = CoroutinesUtils.getSuspendingFunctionReturnType(types[1]);
				functionType = ResolvableType.forClassWithGenerics(
					Function.class,
					ResolvableType.forClassWithGenerics(Flux.class, ResolvableType.forType(continuationArgType)),
					ResolvableType.forClassWithGenerics(Flux.class, ResolvableType.forType(continuationReturnType))
				).getType();
			}
			else if (isValidKotlinSuspendConsumer(functionType, types)) {
				Type continuationArgType = CoroutinesUtils.getSuspendingFunctionArgType(types[0]);
				functionType = ResolvableType.forClassWithGenerics(
					Consumer.class,
					ResolvableType.forClassWithGenerics(Flux.class, ResolvableType.forType(continuationArgType))
				).getType();
			}
			else {
				throw new UnsupportedOperationException("Multi argument Kotlin functions are not currently supported");
			}
			registration = registration.type(functionType);
			return registration;
		}

		private boolean isValidKotlinSupplier(Type functionType) {
			return isTypeRepresentedByClass(functionType, Function0.class);
		}

		private boolean isValidKotlinConsumer(Type functionType, Type[] type) {
			return isTypeRepresentedByClass(functionType, Function1.class) &&
				type.length == 2 &&
				!CoroutinesUtils.isContinuationType(type[0]) &&
				isTypeRepresentedByClass(type[1], Unit.class);
		}

		private boolean isValidKotlinFunction(Type functionType, Type[] type) {
			return isTypeRepresentedByClass(functionType, Function1.class) &&
				type.length == 2 &&
				!CoroutinesUtils.isContinuationType(type[0]) &&
				!isTypeRepresentedByClass(type[1], Unit.class);
		}

		private boolean isValidKotlinSuspendSupplier(Type functionType, Type[] type) {
			return isTypeRepresentedByClass(functionType, Function1.class) &&
				type.length == 2 &&
				CoroutinesUtils.isContinuationFlowType(type[0]);
		}

		private boolean isValidKotlinSuspendConsumer(Type functionType, Type[] type) {
			return isTypeRepresentedByClass(functionType, Function2.class) &&
				type.length == 3 &&
				CoroutinesUtils.isFlowType(type[0]) &&
				CoroutinesUtils.isContinuationUnitType(type[1]);
		}

		private boolean isValidKotlinSuspendFunction(Type functionType, Type[] type) {
			return isTypeRepresentedByClass(functionType, Function2.class) &&
				type.length == 3 &&
				CoroutinesUtils.isContinuationFlowType(type[1]);
		}

		private boolean isTypeRepresentedByClass(Type type, Class<?> clazz) {
			return type.getTypeName().contains(clazz.getName());
		}

		@Override
		public Class<?> getObjectType() {
			return FunctionRegistration.class;
		}

		@Override
		public void setBeanName(String name) {
			this.name = name;
		}

		@Override
		public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
			this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
		}
	}
}
