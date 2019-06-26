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
import java.util.function.Function;

import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.util.MimeType;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;


/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public interface FunctionCatalog {



    /**
     * Will look up the instance of the functional interface. The 'acceptedOutputTypes'
     * will be used by the available {@link MessageConverter}s to convert the values of output streams.
     * This means that regardless of the actual function signature the return types of the function
     * will always be {@link Message}.
     * <br>
     * For example,
     * <br>
     * Assume user function is:
     * <br>
     * {@code
     * Function<Flux<String>, Tuple2<Flux<Foo>>, Mono<String>>>
     * }
     * <br>
     * While you can provide input of any type (the internal conversion routing will kick in),
     * the output type will always be assumed as Message<T> where 'T' is the type returned by
     * the corresponding {@link MessageConverter#toMessage(Object, org.springframework.messaging.MessageHeaders)}
     * method. For example, providing that the existing {@link MessageConverter}s convert to Message<byte[]>, the
     * above user function could be invoked as:
     * <br>
     * {@code
     * Function<Flux<String>, Tuple2<Flux<Message<byte[]>>, Mono<Message<byte[]>>>>
     * }
     * @param functionDefinition the definition of the functional interface. Must not be null;
     * @param acceptedOutputTypes ordered array of accepted mime types to be used to
     * generate output streams. The size of the array must match the count of function output.
     * @return instance of the functional interface registered with this catalog
     */
    default <T> T lookup(String functionDefinition, MimeType... acceptedOutputTypes) {
        throw new UnsupportedOperationException(
                "This instance of FunctionCatalog does not support this operation");
    }


    /**
     * Will look up the instance of the functional interface by name only.
     * @param <T> instance type
     * @param functionDefinition the definition of the functional interface. Must not be null;
     * @return instance of the functional interface registered with this catalog
     */
    default <T> T lookup(String functionDefinition) {
        return this.lookup(null, functionDefinition);
    }

    /**
     * Will look up the instance of the functional interface by name and type which can
     * only be Supplier, Consumer or Function. If type is not provided, the lookup will be
     * made based on name only.
     * @param <T> instance type
     * @param type the type of functional interface. Can be null
     * @param functionDefinition the definition of the functional interface. Must not be null;
     * @return instance of the functional interface registered with this catalog
     */
    <T> T lookup(Class<?> type, String functionDefinition);

    Set<String> getNames(Class<?> type);

    /**
     * Return the count of functions registered in this catalog.
     * @return the count of functions registered in this catalog
     */
    default int size() {
        throw new UnsupportedOperationException(
                "This instance of FunctionCatalog does not support this operation");
    }

}
