/*
 * Copyright 2019-present the original author or authors.
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

package org.springframework.cloud.function.utils;

import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

import org.springframework.core.KotlinDetector;

/**
 * @author Oleg Zhurakousky
 */
public final class KotlinUtils {

	private KotlinUtils() {

	}

	public static boolean isKotlinType(Object object) {
		if (KotlinDetector.isKotlinPresent()) {
			return KotlinDetector.isKotlinType(object.getClass()) || object instanceof Function0<?>
					|| object instanceof Function1<?, ?>;
		}
		return false;
	}

}
