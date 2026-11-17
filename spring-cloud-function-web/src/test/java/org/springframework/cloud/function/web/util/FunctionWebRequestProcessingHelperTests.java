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

package org.springframework.cloud.function.web.util;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.web.FunctionHttpProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the HTTP method eligibility checks of {@link FunctionWebRequestProcessingHelper}.
 */
public class FunctionWebRequestProcessingHelperTests {

	@Test
	void everyFunctionIsValidWhenNoExplicitMappingsAreConfigured() {
		FunctionHttpProperties properties = new FunctionHttpProperties();

		for (String method : new String[] {"GET", "POST", "PUT", "DELETE"}) {
			assertThat(FunctionWebRequestProcessingHelper.isFunctionValidForMethod(method, "anyFunction", properties))
					.as(method).isTrue();
		}
	}

	@Test
	void onlyListedFunctionsAreValidWhenExplicitMappingsAreConfigured() {
		FunctionHttpProperties properties = new FunctionHttpProperties();
		properties.setPost("uppercase;reverse");

		assertThat(FunctionWebRequestProcessingHelper.isFunctionValidForMethod("POST", "uppercase", properties)).isTrue();
		assertThat(FunctionWebRequestProcessingHelper.isFunctionValidForMethod("POST", "reverse", properties)).isTrue();
		assertThat(FunctionWebRequestProcessingHelper.isFunctionValidForMethod("POST", "other", properties)).isFalse();
		// mappings are configured per method, so other methods are unaffected
		assertThat(FunctionWebRequestProcessingHelper.isFunctionValidForMethod("GET", "other", properties)).isTrue();
	}

	@Test
	void mappingsAreMatchedByWholeFunctionNameOnly() {
		FunctionHttpProperties properties = new FunctionHttpProperties();
		properties.setGet("uppercase");

		assertThat(FunctionWebRequestProcessingHelper.isFunctionValidForMethod("GET", "upper", properties)).isFalse();
		assertThat(FunctionWebRequestProcessingHelper.isFunctionValidForMethod("GET", "uppercase2", properties)).isFalse();
	}

	@Test
	void unsupportedHttpMethodIsNeverValid() {
		FunctionHttpProperties properties = new FunctionHttpProperties();

		assertThat(FunctionWebRequestProcessingHelper.isFunctionValidForMethod("PATCH", "anyFunction", properties)).isFalse();
	}

	@Test
	void assertFunctionValidForMethodAcceptsEligibleFunction() {
		FunctionHttpProperties properties = new FunctionHttpProperties();
		properties.setDelete("deleteConsumer");

		FunctionWebRequestProcessingHelper.assertFunctionValidForMethod("DELETE", wrapperOf("deleteConsumer"), properties);
	}

	@Test
	void assertFunctionValidForMethodRejectsIneligibleFunctionWithDescriptiveMessage() {
		FunctionHttpProperties properties = new FunctionHttpProperties();
		properties.setDelete("deleteConsumer");

		assertThatIllegalArgumentException()
				.isThrownBy(() -> FunctionWebRequestProcessingHelper
						.assertFunctionValidForMethod("DELETE", wrapperOf("other"), properties))
				.withMessageContaining("Function 'other' is not eligible to be invoked via  DELETE  method");
	}

	private static FunctionWrapper wrapperOf(String functionDefinition) {
		FunctionInvocationWrapper function = mock(FunctionInvocationWrapper.class);
		when(function.getFunctionDefinition()).thenReturn(functionDefinition);
		return new FunctionWrapper(function, null);
	}

}
