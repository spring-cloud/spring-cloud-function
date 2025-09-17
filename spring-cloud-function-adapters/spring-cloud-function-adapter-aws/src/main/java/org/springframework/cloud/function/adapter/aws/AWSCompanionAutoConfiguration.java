/*
 * Copyright 2021-present the original author or authors.
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

package org.springframework.cloud.function.adapter.aws;

import tools.jackson.databind.ObjectMapper;

import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.CollectionUtils;


/**
 *
 * @author Oleg Zhurakousky
 * @since 3.2
 *
 */
@Configuration
public class AWSCompanionAutoConfiguration {

	@Bean
	public AWSTypesMessageConverter awsTypesMessageConverter(GenericApplicationContext applicationContext) {
		JsonMapper jsonMapper = CollectionUtils.isEmpty(applicationContext.getBeansOfType(JsonMapper.class).values())
				? new JacksonMapper(new ObjectMapper())
						: applicationContext.getBean(JsonMapper.class);
		return new AWSTypesMessageConverter(jsonMapper);
	}
}
