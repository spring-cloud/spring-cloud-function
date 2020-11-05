/*
 * Copyright 2020-2020 the original author or authors.
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

package org.springframework.cloud.function.userissues;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.support.GenericMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class UserIssuesTests {

	private FunctionCatalog configureCatalog(Class<?>... configClass) {
		ApplicationContext context = new SpringApplicationBuilder(configClass).run(
				"--logging.level.org.springframework.cloud.function=DEBUG", "--spring.main.lazy-initialization=true");
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		return catalog;
	}

	@Before
	public void before() {
		System.clearProperty("spring.cloud.function.definition");
	}

	@Test
	public void testIssue602() throws Exception {
		FunctionCatalog catalog = this.configureCatalog(Issue602Configuration.class);
		Function<Message<String>, Integer> function = catalog.lookup("consumer");
		int result = function.apply(
				new GenericMessage<String>("[{\"name\":\"julien\"},{\"name\":\"ricky\"},{\"name\":\"bubbles\"}]"));
		assertThat(result).isEqualTo(3);

	}

	@EnableAutoConfiguration
	@Configuration
	public static class Issue602Configuration {
		@Bean
		public Function<List<Product>, Integer> consumer() {
			return v -> {
				assertThat(v.get(0).getName()).isEqualTo("julien");
				assertThat(v.get(1).getName()).isEqualTo("ricky");
				assertThat(v.get(2).getName()).isEqualTo("bubbles");
				return v.size();
			};
		}

		@Bean
		public ProductMessageConverter productMessageConverter(JsonMapper mapper) {
			return new ProductMessageConverter(mapper);
		}
	}

	private static class Product {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	public static class ProductMessageConverter extends AbstractMessageConverter {

		private final JsonMapper mapper;

		ProductMessageConverter(JsonMapper mapper) {
			this.mapper = mapper;
		}

		@Override
		protected boolean canConvertFrom(Message<?> message, Class<?> targetClass) {
			return true;
		}

		@Override
		protected boolean supports(Class<?> clazz) {
			return true;
		}

		@Override
		protected Object convertFromInternal(Message<?> message, Class<?> targetClass, Object conversionHint) {
			Object result = mapper.fromJson((String) message.getPayload(), (Type) conversionHint);
			return result;
		}

	}
}
