/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.cloud.function.context;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;

/**
 * @author Oleg Zhurakousky
 * @since 2.0.1
 *
 */
public abstract class AbstractFunctionRegistry
		implements FunctionRegistry, FunctionInspector, ApplicationEventPublisherAware {

	@Autowired
	private Environment environment = new StandardEnvironment();

	protected ApplicationEventPublisher applicationEventPublisher;

	public <T> T lookup(Class<?> type, String name) {
		String functionDefinitionName = !StringUtils.hasText(name)
				&& this.environment.containsProperty("spring.cloud.function.definition")
						? this.environment.getProperty("spring.cloud.function.definition")
						: name;
		return this.doLookup(type, functionDefinitionName);
	}

	protected abstract <T> T doLookup(Class<?> type, String name);

	@Override
	public void setApplicationEventPublisher(
			ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

}
