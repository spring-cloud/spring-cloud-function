/*
 * Copyright 2026-present the original author or authors.
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.config.KotlinLambdaToFunctionAutoConfiguration.KotlinFunctionWrapper;
import org.springframework.core.ResolvableType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for {@link KotlinFunctionWrapper}.
 *
 * @author Seonwoo Jung
 */
public class KotlinFunctionWrapperTests {

	@Test
	public void emptyStringInputIsPassedToFunction() {
		Function1<String, String> function = value -> "[" + value + "]";
		KotlinFunctionWrapper wrapper = new KotlinFunctionWrapper(function);

		assertThat(wrapper.apply("")).isEqualTo("[]");
	}

	@Test
	public void emptyValuesArePassedToFunction() {
		Function1<Object, Object> function = value -> value;
		KotlinFunctionWrapper wrapper = new KotlinFunctionWrapper(function);

		List<Object> emptyList = Collections.emptyList();
		assertThat(wrapper.apply(emptyList)).isSameAs(emptyList);
		assertThat(wrapper.apply(Collections.emptyMap())).isEqualTo(Collections.emptyMap());
		assertThat(wrapper.apply(Optional.empty())).isEqualTo(Optional.empty());
		Object[] emptyArray = new Object[0];
		assertThat(wrapper.apply(emptyArray)).isSameAs(emptyArray);
	}

	@Test
	public void nullInputInvokesSupplier() {
		Function0<String> supplier = () -> "hello";
		KotlinFunctionWrapper wrapper = new KotlinFunctionWrapper(supplier);

		assertThat(wrapper.apply(null)).isEqualTo("hello");
		assertThat(wrapper.get()).isEqualTo("hello");
	}

	@Test
	public void onlyTrailingRegistrationSuffixIsRemoved() {
		String beanName = "event" + FunctionRegistration.REGISTRATION_NAME_SUFFIX;
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		RootBeanDefinition definition = new RootBeanDefinition();
		definition.setTargetType(ResolvableType.forClassWithGenerics(Function1.class, String.class, String.class));
		beanFactory.registerBeanDefinition(beanName, definition);

		KotlinFunctionWrapper wrapper = new KotlinFunctionWrapper((Function1<String, String>) value -> value);
		wrapper.setName(beanName + FunctionRegistration.REGISTRATION_NAME_SUFFIX);
		wrapper.setBeanFactory(beanFactory);

		FunctionRegistration<?> registration = wrapper.getFunctionRegistration();
		assertThat(registration.getNames()).containsExactly(beanName);
		assertThat(FunctionTypeUtils.isFunction(registration.getType())).isTrue();
	}

	@Test
	public void nonParameterizedFunctionTypeIsReported() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		KotlinFunctionWrapper wrapper = new KotlinFunctionWrapper((Function1<String, String>) value -> value);
		wrapper.setName("unknown");
		wrapper.setBeanFactory(beanFactory);

		assertThatIllegalStateException().isThrownBy(wrapper::getFunctionRegistration)
			.withMessageContaining("'unknown'");
	}

	@Test
	public void functionTakingSupplierIsNotMistakenForSupplier() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		RootBeanDefinition definition = new RootBeanDefinition();
		definition.setTargetType(ResolvableType.forClassWithGenerics(Function1.class,
				ResolvableType.forClassWithGenerics(Function0.class, String.class), ResolvableType.forClass(String.class)));
		beanFactory.registerBeanDefinition("invoker", definition);

		KotlinFunctionWrapper wrapper = new KotlinFunctionWrapper((Function1<Function0<String>, String>) Function0::invoke);
		wrapper.setName("invoker");
		wrapper.setBeanFactory(beanFactory);

		ResolvableType type = ResolvableType.forType(wrapper.getFunctionRegistration().getType());
		assertThat(type.toClass()).isEqualTo(Function.class);
		assertThat(type.getGeneric(0).toClass()).isEqualTo(Function0.class);
		assertThat(type.getGeneric(1).toClass()).isEqualTo(String.class);
	}

}
