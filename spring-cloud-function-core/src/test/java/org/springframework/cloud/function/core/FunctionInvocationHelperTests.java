/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.cloud.function.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the pass-through defaults of {@link FunctionInvocationHelper}.
 */
class FunctionInvocationHelperTests {

	private final FunctionInvocationHelper<String> helper = new FunctionInvocationHelper<>() { };

	@Test
	void retainsOutputAsMessageByDefault() {
		assertThat(this.helper.isRetainOutputAsMessage("in")).isTrue();
	}

	@Test
	void preProcessInputReturnsInputUnchanged() {
		assertThat(this.helper.preProcessInput("in", new Object())).isEqualTo("in");
	}

	@Test
	void postProcessResultReturnsResultUnchanged() {
		Object result = new Object();
		assertThat(this.helper.postProcessResult(result, "in")).isSameAs(result);
		assertThat(this.helper.postProcessResult(null, "in")).isNull();
	}

}
