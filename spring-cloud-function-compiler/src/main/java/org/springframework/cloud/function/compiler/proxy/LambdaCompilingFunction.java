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
import java.util.function.Function;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.function.compiler.CompiledFunctionFactory;
import org.springframework.cloud.function.compiler.FunctionCompiler;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;

/**
 * @author Mark Fisher
 */
public class LambdaCompilingFunction<T, R> implements Function<T, R>, InitializingBean, BeanNameAware {

	private final Resource resource;

	private final FunctionCompiler<T, R> compiler;

	private String beanName;

	private Function<T, R> compiledFunction;

	public LambdaCompilingFunction(Resource resource, FunctionCompiler<T, R> compiler) {
		Assert.notNull(resource, "Resource must not be null");
		Assert.notNull(compiler, "FunctionCompiler must not be null");
		this.resource = resource;
		this.compiler = compiler;
	}

	@Override
	public void setBeanName(String beanName) {
		this.beanName = beanName;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		String lambda = FileCopyUtils.copyToString(new InputStreamReader(this.resource.getInputStream()));
		CompiledFunctionFactory<Function<T, R>> factory = this.compiler.compile(this.beanName, lambda);
		this.compiledFunction = factory.getResult();
	}

	@Override
	public R apply(T input) {
		return this.compiledFunction.apply(input);
	}
}
