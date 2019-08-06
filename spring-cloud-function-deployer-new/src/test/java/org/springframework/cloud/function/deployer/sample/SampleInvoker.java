/*
 * Copyright 2017-2019 the original author or authors.
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

package org.springframework.cloud.function.deployer.sample;

import java.util.function.Function;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.deployer.ApplicationContainer;
import org.springframework.cloud.function.deployer.FunctionDeployerBootstrap;
import org.springframework.cloud.function.deployer.FunctionProperties;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.0
 */
public class SampleInvoker extends ApplicationContainer {

	public static void main(String[] args) throws Exception {
		SampleInvoker invokerByClass = FunctionDeployerBootstrap.instance(
				"--spring.cloud.function.location=/Users/olegz/Downloads/simple-function-app/jars/simple-function-jar-0.0.1-SNAPSHOT.jar",
				"--spring.cloud.function.function-class=oz.function.simplefunctionapp.UpperCaseFunction")
		.run(SampleInvoker.class, args);

		System.out.println(invokerByClass.uppercase("eric"));
		System.out.println(invokerByClass.uppercase("oleg"));

		SampleInvoker invokerByBean = FunctionDeployerBootstrap.instance(
				"--spring.cloud.function.location=/Users/olegz/Downloads/simple-function-app/jars/simple-function-app-0.0.1-SNAPSHOT.jar",
				"--spring.cloud.function.function-name=uppercase")
		.run(SampleInvoker.class, args);

		System.out.println(invokerByBean.uppercase("eric"));
		System.out.println(invokerByBean.uppercase("oleg"));
	}

	public SampleInvoker(FunctionCatalog functionCatalog, FunctionInspector functionInspector,
			FunctionProperties functionProperties) {
		super(functionCatalog, functionInspector, functionProperties);
	}

	public String uppercase(String value) {
		Function<String, String> functon = this.getFunction();
		return functon.apply(value);
	}

}
