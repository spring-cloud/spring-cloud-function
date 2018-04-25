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

public class Frenchizer implements Function<Integer, String> {

	private String[] numbers;

	@PostConstruct
	public void init() {
		this.numbers = new String[4];
		numbers[0] = "un";
		numbers[1] = "deux";
		numbers[2] = "trois";
		numbers[3] = "quatre";
	}

	@Override
	public String apply(Integer integer) {
		if (integer < this.numbers.length + 1) {
			return this.numbers[integer - 1];
		}
		throw new RuntimeException();
	}
}
