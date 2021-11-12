/*
 * Copyright 2019-2020 the original author or authors.
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

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.cloud.function.context.FunctionProperties;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.core.FunctionInvocationHelper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.util.StringUtils;

/**
 * Implementation of {@link FunctionRegistry} capable of discovering functioins in {@link BeanFactory}.
 *
 * @author Oleg Zhurakousky
 */
public class BeanFactoryAwareFunctionRegistry extends SimpleFunctionRegistry implements ApplicationContextAware {

	private GenericApplicationContext applicationContext;

	public BeanFactoryAwareFunctionRegistry(ConversionService conversionService, CompositeMessageConverter messageConverter,
			JsonMapper jsonMapper, @Nullable FunctionProperties functionProperties, @Nullable FunctionInvocationHelper<Message<?>> functionInvocationHelper) {
		super(conversionService, messageConverter, jsonMapper, functionProperties, functionInvocationHelper);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = (GenericApplicationContext) applicationContext;
	}

	/*
	 * Basically gives an approximation only including function registrations and SFC.
	 * Excludes possible POJOs that can be treated as functions
	 */
	@Override
	public int size() {
		return this.applicationContext.getBeanNamesForType(Supplier.class).length +
			this.applicationContext.getBeanNamesForType(Function.class).length +
			this.applicationContext.getBeanNamesForType(Consumer.class).length +
			super.size();
	}

	/*
	 * Doesn't account for POJO so we really don't know until it's been lookedup
	 */
	@Override
	public Set<String> getNames(Class<?> type) {
		Set<String> registeredNames = super.getNames(type);
		if (type == null) {
			registeredNames
				.addAll(Arrays.asList(this.applicationContext.getBeanNamesForType(Function.class)));
			registeredNames
				.addAll(Arrays.asList(this.applicationContext.getBeanNamesForType(Supplier.class)));
			registeredNames
				.addAll(Arrays.asList(this.applicationContext.getBeanNamesForType(Consumer.class)));
			registeredNames
				.addAll(Arrays.asList(this.applicationContext.getBeanNamesForType(FunctionRegistration.class)));
		}
		else {
			registeredNames.addAll(Arrays.asList(this.applicationContext.getBeanNamesForType(type)));
		}
		return registeredNames;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public <T> T lookup(Class<?> type, String functionDefinition, String... expectedOutputMimeTypes) {
		functionDefinition = StringUtils.hasText(functionDefinition)
				? functionDefinition
						: this.applicationContext.getEnvironment().getProperty(FunctionProperties.FUNCTION_DEFINITION, "");

		functionDefinition = this.normalizeFunctionDefinition(functionDefinition);
		if (!StringUtils.hasText(functionDefinition)) {
			logger.info("Can't determine default function definition. Please "
					+ "use 'spring.cloud.function.definition' property to explicitly define it.");
			return null;
		}
		FunctionInvocationWrapper function = this.doLookup(type, functionDefinition, expectedOutputMimeTypes);

		if (function == null) {
			Set<String> functionRegistratioinNames = super.getNames(null);
			String[] functionNames = StringUtils.delimitedListToStringArray(functionDefinition.replaceAll(",", "|").trim(), "|");
			for (String functionName : functionNames) {
				if (functionRegistratioinNames.contains(functionName) && logger.isDebugEnabled()) {
					logger.debug("Skipping function '" + functionName + "' since it is already present");
				}
				else {
					Object functionCandidate = this.discoverFunctionInBeanFactory(functionName);
					if (functionCandidate != null) {
						Type functionType = null;
						FunctionRegistration functionRegistration = null;
						if (functionCandidate instanceof FunctionRegistration) {
							functionRegistration = (FunctionRegistration) functionCandidate;
						}
						else if (this.isFunctionPojo(functionCandidate, functionName)) {
							Method functionalMethod = FunctionTypeUtils.discoverFunctionalMethod(functionCandidate.getClass());
							functionCandidate = this.proxyTarget(functionCandidate, functionalMethod);
							functionType = FunctionTypeUtils.fromFunctionMethod(functionalMethod);
						}
						else if (this.isSpecialFunctionRegistration(functionNames, functionName)) {
							functionRegistration = this.applicationContext
									.getBean(functionName + FunctionRegistration.REGISTRATION_NAME_SUFFIX, FunctionRegistration.class);
						}
						else {
							functionType = FunctionTypeUtils.discoverFunctionType(functionCandidate, functionName, this.applicationContext);
						}
						if (functionRegistration == null) {
							functionRegistration = new FunctionRegistration(functionCandidate, functionName).type(functionType);
						}
						// Certain Kafka Streams functions such as KStream[] return types could be null (esp when using Kotlin).
						if (functionRegistration != null) {
							this.register(functionRegistration);
						}
					}
					else {
						if (logger.isDebugEnabled()) {
							logger.debug("Function '" + functionName + "' is not available in FunctionCatalog or BeanFactory");
						}
					}
				}
			}
			function = super.doLookup(type, functionDefinition, expectedOutputMimeTypes);
		}

		return (T) function;
	}

	private Object discoverFunctionInBeanFactory(String functionName) {
		Object functionCandidate = null;
		if (this.applicationContext.containsBean(functionName)) {
			functionCandidate = this.applicationContext.getBean(functionName);
		}
		else {
			try {
				functionCandidate = BeanFactoryAnnotationUtils.qualifiedBeanOfType(this.applicationContext.getBeanFactory(), Object.class, functionName);
			}
			catch (Exception e) {
				// ignore since there is no safe isAvailable-kind of method
			}
		}
		return functionCandidate;
	}

	@Override
	protected boolean containsFunction(String functionName) {
		return super.containsFunction(functionName) ? true : this.applicationContext.containsBean(functionName);
	}

	private boolean isFunctionPojo(Object functionCandidate, String functionName) {
		return !functionCandidate.getClass().isSynthetic()
			&& !(functionCandidate instanceof Supplier)
			&& !(functionCandidate instanceof Function)
			&& !(functionCandidate instanceof Consumer)
			&& !this.applicationContext.containsBean(functionName + FunctionRegistration.REGISTRATION_NAME_SUFFIX);
	}

	/**
	 * At the moment 'special function registration' simply implies that a bean under the provided functionName
	 * may have already been wrapped and registered as FunuctionRegistration with BeanFactory under the name of
	 * the function suffixed with {@link FunctionRegistration#REGISTRATION_NAME_SUFFIX}
	 * (e.g., 'myKotlinFunction_registration').
	 * <br><br>
	 * At the moment only Kotlin module does this
	 *
	 * @param functionCandidate candidate for FunctionInvocationWrapper instance
	 * @param functionName the name of the function
	 * @return true if this function candidate qualifies
	 */
	private boolean isSpecialFunctionRegistration(Object functionCandidate, String functionName) {
		return this.applicationContext.containsBean(functionName + FunctionRegistration.REGISTRATION_NAME_SUFFIX);
	}

	private Object proxyTarget(Object targetFunction, Method actualMethodToCall) {
		ProxyFactory pf = new ProxyFactory(targetFunction);
		pf.setProxyTargetClass(true);
		pf.setInterfaces(Function.class);
		pf.addAdvice(new MethodInterceptor() {
			@Override
			public Object invoke(MethodInvocation invocation) throws Throwable {
				return actualMethodToCall.invoke(invocation.getThis(), invocation.getArguments());
			}
		});
		return pf.getProxy();
	}
}
