/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.function.context.catalog.observability;

import io.micrometer.api.instrument.docs.DocumentedObservation;
import io.micrometer.api.instrument.docs.TagKey;

enum FunctionObservation implements DocumentedObservation {

	/**
	 * Observation created around a function execution
	 */
	FUNCTION_OBSERVATION {
		@Override
		public String getName() {
			return "spring.function";
		}

		@Override
		public String getContextualName() {
			return "function";
		}

		@Override
		public TagKey[] getLowCardinalityTagKeys() {
			return FunctionLowCardinalityTags.values();
		}

		@Override
		public String getPrefix() {
			return "spring.function";
		}
	};

	enum FunctionLowCardinalityTags implements TagKey {

		/**
		 * Name of the function.
		 */
		FUNCTION_NAME {
			@Override
			public String getKey() {
				return "spring.function.name";
			}
		}

	}
}
