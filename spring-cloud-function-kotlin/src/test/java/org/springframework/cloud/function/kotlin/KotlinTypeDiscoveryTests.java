/*
 * Copyright 2019-2025 the original author or authors.
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

package org.springframework.cloud.function.kotlin;



import java.lang.reflect.Type;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class KotlinTypeDiscoveryTests {

	@Test
	public void testOutputInputTypes() {
		Type functionType = FunctionTypeUtils.discoverFunctionTypeFromClass(KotlinComponentMessageFunction.class);
		Type outputType = FunctionTypeUtils.getOutputType(functionType);
		assertThat(FunctionTypeUtils.isMessage(outputType)).isTrue();

		Type inputType = FunctionTypeUtils.getInputType(functionType);
		assertThat(FunctionTypeUtils.isMessage(inputType)).isTrue();
	}
}
