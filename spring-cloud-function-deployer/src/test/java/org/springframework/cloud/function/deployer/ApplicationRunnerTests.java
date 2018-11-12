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

package org.springframework.cloud.function.deployer;

import org.junit.Test;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.test.Doubler;
import org.springframework.cloud.function.test.FunctionApp;
import org.springframework.cloud.function.test.FunctionRegistrar;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 */

public class ApplicationRunnerTests {

	@Test
	public void startEvaluateAndStop() {
		ApplicationRunner runner = new ApplicationRunner(getClass().getClassLoader(),
				FunctionApp.class.getName());
		runner.run("--spring.main.webEnvironment=false");
		assertThat(runner.containsBean(Doubler.class.getName())).isTrue();
		assertThat(runner.getBean(Doubler.class.getName())).isNotNull();
		runner.close();
	}

	@Test
	public void functional() {
		ApplicationRunner runner = new ApplicationRunner(getClass().getClassLoader(),
				FunctionRegistrar.class.getName());
		runner.run();
		assertThat(runner.containsBean(Doubler.class.getName())).isFalse();
		assertThat(runner.getBean(FunctionCatalog.class.getName())).isNotNull();
		assertThat(runner.getBeanNames(FunctionRegistration.class.getName())).hasSize(2);
		runner.close();
	}
}
