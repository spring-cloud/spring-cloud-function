/*
 * Copyright 2016-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.context;

import java.util.Set;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public interface FunctionCatalog {

	/**
	 * Will look up the instance of the functional interface by name only
	 *
	 * @param name the name of the functional interface. Must not be null;
	 * @return instance of the functional interface registered with this catalog
	 */
	default public <T> T lookup(String name) {
		return this.lookup(null, name);
	}

	/**
	 * Will look up the instance of the functional interface by name and type
	 * which can only be Supplier, Consumer or Function. If type is not provided, the lookup
	 * will be made based on name only
	 *
	 * @param type the type of functional interface. Can be null
	 * @param name the name of the functional interface. Must not be null;
	 * @return instance of the functional interface registered with this catalog
	 */
	<T> T lookup(Class<?> type, String name);

	Set<String> getNames(Class<?> type);

	/**
	 * Return the count of functions registered in this catalog
	 * @return the count of functions registered in this catalog
	 */
	default int size() {
		throw new UnsupportedOperationException("This instance of FunctionCatalog does not support this operation");
	}

}
