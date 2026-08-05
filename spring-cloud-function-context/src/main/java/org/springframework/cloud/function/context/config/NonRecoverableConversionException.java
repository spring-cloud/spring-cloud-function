/*
 * Copyright 2020-present the original author or authors.
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

package org.springframework.cloud.function.context.config;

import org.springframework.lang.Nullable;
import org.springframework.messaging.converter.MessageConversionException;

/**
 * Signals that input conversion failed in a way the caller has explicitly asked
 * not to be silently swallowed (see {@code failOnJsonError} in
 * {@link org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry}),
 * as opposed to an ordinary conversion miss that a fallback (e.g. a
 * {@code ConversionService}) may still recover from.
 *
 * <p>This is a distinct subtype specifically so that downstream error handling
 * (e.g. a web framework's exception resolver) can recognize a definitive,
 * non-recoverable conversion failure without needing to special-case every
 * exception that conversion might otherwise throw.
 *
 * @author KOMUNE
 * @since 5.0.3
 */
@SuppressWarnings("serial")
public class NonRecoverableConversionException extends MessageConversionException {

	public NonRecoverableConversionException(String description, @Nullable Throwable cause) {
		super(description, cause);
	}

}
