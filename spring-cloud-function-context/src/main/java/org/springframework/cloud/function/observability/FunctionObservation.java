/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.function.observability;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * @author Marcin Grzejszczak
 * @author Oleg Zhurakousky
 * @since 4.0.0
 */
enum FunctionObservation implements ObservationDocumentation {

	/**
	 * Observation created around processing a message (functional bean processing).
	 */
	FUNCTION_PROCESSING_OBSERVATION {
		@Override
		public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultFunctionObservationConvention.class;
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return FunctionLowCardinalityTags.values();
		}

		@Override
		public String getPrefix() {
			return "spring.cloud.function";
		}
	};

	enum FunctionLowCardinalityTags implements KeyName {

		/**
		 * Name of the function.
		 */
		FUNCTION_NAME {
			public String asString() {
				return "spring.cloud.function.definition";
			}
		}

	}

}
