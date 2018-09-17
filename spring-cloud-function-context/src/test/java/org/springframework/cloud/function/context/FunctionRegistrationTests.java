/*
 * Copyright 2016-2017 the original author or authors.
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

import java.util.function.Function;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class FunctionRegistrationTests {

	@Test
	public void noTypeByDefault() {
		FunctionRegistration<?> registration = new FunctionRegistration<>(new Foos(),
				"foos");
		assertThat(registration.getType()).isNull();
		assertThat(registration.getNames()).contains("foos");
	}

	@Test
	public void wrap() {
		FunctionRegistration<Foos> registration = new FunctionRegistration<>(new Foos(),
				"foos").type(FunctionType.of(Foos.class).getType());
		FunctionRegistration<?> other = registration.wrap();
		assertThat(registration.getType().isWrapper()).isFalse();
		assertThat(other.getType().isWrapper()).isTrue();
		assertThat(other.getTarget()).isNotEqualTo(registration.getTarget());
	}

	private static class Foos implements Function<Integer, String> {
		@Override
		public String apply(Integer t) {
			return "i=" + t;
		}
	}

}
