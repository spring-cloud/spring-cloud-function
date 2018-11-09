/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.function.test;

import org.springframework.boot.SpringApplication;
import org.springframework.cloud.function.context.FunctionalSpringApplication;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;

/**
 * @author Dave Syer
 */
public class FunctionInitializer
		implements ApplicationContextInitializer<GenericApplicationContext> {

	@Bean
	public Doubler myDoubler() {
		return new Doubler();
	}

	@Bean
	public Frenchizer myFrenchizer() {
		return new Frenchizer();
	}

	public static void main(String[] args) throws Exception {
		SpringApplication application = new FunctionalSpringApplication(
				FunctionInitializer.class);
		application.run(args);
	}

	@Override
	public void initialize(GenericApplicationContext context) {
		// TODO: support for FunctionRegistration
		context.registerBean("myDoubler", Doubler.class, () -> myDoubler());
		context.registerBean("myFrenchizer", Frenchizer.class, () -> myFrenchizer());
	}
}
