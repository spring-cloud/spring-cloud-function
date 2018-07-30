/*
 * Copyright 2012-2015 the original author or authors.
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

package org.springframework.cloud.function.web.mvc;

import java.net.URI;
import java.util.function.Supplier;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.function.web.RestApplication;
import org.springframework.cloud.function.web.mvc.SingletonTests.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes= {RestApplication.class, TestConfiguration.class})
public class SingletonTests {

	@LocalServerPort
	private int port;
	@Autowired
	private TestRestTemplate rest;

	@Test
	public void words() throws Exception {
		ResponseEntity<String> result = rest.exchange(
				RequestEntity.get(new URI("/words")).build(), String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody()).isEqualTo("[\"foo\",\"bar\"]");
	}

	@EnableAutoConfiguration
	@org.springframework.boot.test.context.TestConfiguration
	protected static class TestConfiguration {
		@Bean
		public static BeanDefinitionRegistryPostProcessor processor() {
			return new BeanDefinitionRegistryPostProcessor() {

				@Override
				public void postProcessBeanFactory(
						ConfigurableListableBeanFactory beanFactory)
						throws BeansException {
				}

				@Override
				public void postProcessBeanDefinitionRegistry(
						BeanDefinitionRegistry registry) throws BeansException {
					// Simulates what happens when you add a compiled function
					RootBeanDefinition beanDefinition = new RootBeanDefinition(MySupplier.class);
					registry.registerBeanDefinition("words", beanDefinition);
				}
			};
		}
	}
	
	static class MySupplier implements Supplier<Flux<String>> {
		@Override
		public Flux<String> get() {
			return Flux.just("foo", "bar");
		}
	}
}
