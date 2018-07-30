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

package org.springframework.cloud.function.web.constants;

/**
 * Common storage for web request attribute names (in a separate package to avoid cycles).
 *
 * @author Dave Syer
 */
public abstract class WebRequestConstants {

	public static final String FUNCTION = WebRequestConstants.class.getName()
			+ ".function";
	public static final String CONSUMER = WebRequestConstants.class.getName()
			+ ".consumer";
	public static final String SUPPLIER = WebRequestConstants.class.getName()
			+ ".supplier";
	public static final String ARGUMENT = WebRequestConstants.class.getName()
			+ ".argument";
	public static final String HANDLER = WebRequestConstants.class.getName()
			+ ".handler";

}
