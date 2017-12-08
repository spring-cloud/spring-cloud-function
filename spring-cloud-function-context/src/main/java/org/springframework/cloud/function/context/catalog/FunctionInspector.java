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

package org.springframework.cloud.function.context.catalog;

import java.lang.reflect.Type;
import java.util.Optional;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Dave Syer
 *
 */
public interface FunctionInspector {

	boolean isMessage(Object function);

	Class<?> getInputType(Object function);

	Class<?> getOutputType(Object function);

	Class<?> getInputWrapper(Object function);

	Class<?> getOutputWrapper(Object function);

	Object convert(Object function, String value);

	String getName(Object function);

	// Maybe make this a default method?
	static boolean isWrapper(Type type) {
		return Flux.class.equals(type) || Mono.class.equals(type)
				|| Optional.class.equals(type);
	}

}
