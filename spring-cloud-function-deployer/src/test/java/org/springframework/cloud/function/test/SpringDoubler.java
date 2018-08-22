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

import java.util.function.Function;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class SpringDoubler implements Function<Integer, Integer> {

	@Autowired
	private ConfigurableApplicationContext context;

	@PostConstruct
	public void init() {
		if (this.context == null) {
			context = new SpringApplicationBuilder(FunctionApp.class).bannerMode(Mode.OFF).registerShutdownHook(false)
					.web(WebApplicationType.NONE).run();
		}
	}

	@PreDestroy
	public void close() {
		if (context != null) {
			context.close();
		}
	}

	@Override
	public Integer apply(Integer integer) {
		return 2 * integer;
	}
}
