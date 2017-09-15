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

import java.io.InputStreamReader;
import java.lang.reflect.Method;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.function.compiler.AbstractFunctionCompiler;
import org.springframework.cloud.function.compiler.CompiledFunctionFactory;
import org.springframework.cloud.function.core.FunctionFactoryMetadata;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;

/**
 * @author Mark Fisher
 */
public class AbstractLambdaCompilingProxy<T> implements InitializingBean, BeanNameAware, FunctionFactoryMetadata<T> {

	private final Resource resource;

	private final AbstractFunctionCompiler<T> compiler;

	private String beanName;

	private CompiledFunctionFactory<T> factory;

	private String[] typeParameterizations;

	public AbstractLambdaCompilingProxy(Resource resource, AbstractFunctionCompiler<T> compiler) {
		Assert.notNull(resource, "Resource must not be null");
		Assert.notNull(compiler, "Compiler must not be null");
		this.resource = resource;
		this.compiler = compiler;
	}

	@Override
	public void setBeanName(String beanName) {
		this.beanName = beanName;
	}

	public void setTypeParameterizations(String... typeParameterizations) {
		this.typeParameterizations = typeParameterizations;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		String lambda = FileCopyUtils.copyToString(new InputStreamReader(this.resource.getInputStream()));
		this.factory = this.compiler.compile(this.beanName, lambda, this.typeParameterizations);
	}

	@Override
	public final T getTarget() {
		return this.factory.getResult();
	}

	@Override
	public Method getFactoryMethod() {
		return this.factory.getFactoryMethod();
	}
}
