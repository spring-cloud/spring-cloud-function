/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.gateway;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.springframework.scheduling.Trigger;

/**
 * @author Mark Fisher
 */
public interface FunctionGateway {

	<T, R> R invoke(String functionName, T request);

	<T, R> void schedule(String functionName, Trigger trigger, Supplier<T> supplier, Consumer<R> consumer);

	<T, R> void subscribe(Publisher<T> publisher, String functionName, final Consumer<R> consumer);

}
