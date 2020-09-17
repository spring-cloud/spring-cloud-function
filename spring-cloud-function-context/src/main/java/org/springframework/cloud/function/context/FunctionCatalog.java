/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.cloud.function.context;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.activation.MimeType;


/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public interface FunctionCatalog {

	/**
	 * Will look up the instance of the functional interface by name only.
	 *
	 * @param                    <T> instance type
	 * @param functionDefinition the definition of the functional interface. Must
	 *                           not be null;
	 * @return instance of the functional interface registered with this catalog
	 */
	default <T> T lookup(String functionDefinition) {
		return this.lookup(null, functionDefinition, (String[]) null);
	}

	/**
	 * Will look up the instance of the functional interface by name and type which
	 * can only be Supplier, Consumer or Function. If type is not provided, the
	 * lookup will be made based on name only.
	 *
	 * @param                    <T> instance type
	 * @param type               the type of functional interface. Can be null
	 * @param functionDefinition the definition of the functional interface. Must
	 *                           not be null;
	 * @return instance of the functional interface registered with this catalog
	 */
	default <T> T lookup(Class<?> type, String functionDefinition) {
		return this.lookup(type, functionDefinition, (String[]) null);
	}


	/**
	 * Will look up the instance of the functional interface by name only.
	 * This lookup method assumes a very specific semantics which are: <i>function sub-type(s)
	 * expected to be {@code Message<byte[]>}</i>. <br>
	 * For example,
	 * <br><br>
	 * {@code Function<Message<byte[]>, Message<byte[]>>} or
	 * <br>
	 * {@code Function<Flux<Message<byte[]>>, Flux<Message<byte[]>>>} or
	 * <br>
	 * {@code Consumer<Flux<Message<Flux<Message<byte[]>>>} etc. . .
	 * <br><br>
	 * The {@code acceptedOutputMimeTypes} are the string representation of {@link MimeType} where each
	 * mime-type in the provided array would correspond to the output with the same index
	 * (for cases of functions with multiple outputs) and is used to convert such output back
	 * to {@code Message<byte[]>}.
	 * If you need to provide several accepted types per specific output you can simply delimit
	 * them with comma (e.g., {@code application/json,text/plain...}).
	 *
	 * @param  <T> instance type which should be one of {@link Supplier}, {@link Function} or {@link Consumer}.
	 * @param functionDefinition  the definition of a function (e.g., 'foo' or 'foo|bar')
	 * @param acceptedOutputMimeTypes acceptedOutputMimeTypes array of string representation of {@link MimeType}s
	 * 						used to convert function output back to {@code Message<byte[]>}.
	 * @return instance of the functional interface registered with this catalog
	 */
	default <T> T lookup(String functionDefinition, String... expectedOutputMimeTypes) {
		return this.lookup(null, functionDefinition, expectedOutputMimeTypes);
	}

	<T> T lookup(Class<?> type, String functionDefinition, String... expectedOutputMimeTypes); //{
//		throw new UnsupportedOperationException("This instance of FunctionCatalog does not support this operation");
//	}





	Set<String> getNames(Class<?> type);

	/**
	 * Return the count of functions registered in this catalog.
	 *
	 * @return the count of functions registered in this catalog
	 */
	default int size() {
		throw new UnsupportedOperationException("This instance of FunctionCatalog does not support this operation");
	}

}
