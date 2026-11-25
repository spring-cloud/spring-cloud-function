/*
 * Copyright 2012-present the original author or authors.
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

package org.springframework.cloud.function.web.source;

import java.util.function.Supplier;

/**
 * Strategy to determine the destination of a value exported by a supplier. The
 * destination is substituted as is into the URL template of the sink (by default
 * {@code http://${destination}}, so it determines the host that is called) and into
 * the configured request headers. Implementations must therefore only return values
 * that are trusted, and must not derive them from unvalidated data of the exported
 * value or of its headers.
 *
 * @author Dave Syer
 *
 */
public interface DestinationResolver {

	/**
	 * Determine the destination for a value.
	 * @param supplier the exported supplier
	 * @param name the name of the exported supplier or function definition
	 * @param value the value to be sent
	 * @return the destination, which must be a trusted value
	 */
	String destination(Supplier<?> supplier, String name, Object value);

}
