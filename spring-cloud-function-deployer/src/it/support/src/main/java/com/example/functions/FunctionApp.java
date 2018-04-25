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

package com.example.functions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * @author Dave Syer
 */
@SpringBootApplication
public class FunctionApp {
	
	@Bean
	public DoubleLogger myDoubler() {
		return new DoubleLogger();
	}

	@Bean
	public Emitter myEmitter() {
		return new Emitter();
	}

	@Bean
	public LengthCounter myCounter() {
		return new LengthCounter();
	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(FunctionApp.class, args);
	}
}
