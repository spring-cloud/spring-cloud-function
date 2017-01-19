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

package com.example;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.compiler.proxy.ByteCodeLoadingConsumer;
import org.springframework.cloud.function.compiler.proxy.ByteCodeLoadingFunction;
import org.springframework.cloud.function.compiler.proxy.ByteCodeLoadingSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import reactor.core.publisher.Flux;

@SpringBootApplication
@EnableConfigurationProperties(FunctionProperties.class)
public class SampleApplication {

	@Autowired
	private FunctionProperties properties;

	@Bean
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@ConditionalOnProperty(name="function.type", havingValue="supplier")
	public Supplier<Flux<String>> supplier() {
		return new ByteCodeLoadingSupplier(properties.getResource());
	}

	@Bean
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@ConditionalOnProperty(name="function.type", havingValue="function")
	public Function<Flux<String>, Flux<String>> function() {
		return new ByteCodeLoadingFunction(properties.getResource());
	}

	@Bean
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@ConditionalOnProperty(name="function.type", havingValue="consumer")
	public Consumer<String> consumer() {
		return new ByteCodeLoadingConsumer(properties.getResource());
	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(SampleApplication.class, args);
	}
}

@ConfigurationProperties("function")
class FunctionProperties {

	private String type = "function";

	private Resource resource;

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Resource getResource() {
		return resource;
	}

	public void setResource(Resource resource) {
		this.resource = resource;
	}
}
