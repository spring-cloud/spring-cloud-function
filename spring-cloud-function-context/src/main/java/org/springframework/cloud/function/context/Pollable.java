/*
 * Copyright 2019-2019 the original author or authors.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Supplier;

import org.springframework.context.annotation.Bean;

/**
 *
 * A marker annotation to signal to the consumers of the
 * annotated {@link Supplier} method that regardless of its type signature
 * (reactive or imperative), such supplier needs to be polled
 * periodically. This has special significance to the reactive suppliers (e.g., {@code Supplier<Flux<?>}),
 * since in most cases they are treated as producers of an infinite stream
 * that is managed independently once produced. However if such suppliers produce a stream hat is finite
 * they may need to be called again.
 *
 * <br>
 * NOTE: Given that polling behavior is specific to the users (consumers) of the annotated supplier,
 * spring-cloud-function provides no default post processing behavior which means that annotating a
 * factory method with this annotation will not have any effect without some application/framework
 * specific post processing.
 *
 *
 * @author Oleg Zhurakousky
 * @since 3.0
 *
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Bean
@Documented
public @interface Pollable {

	/**
	 * Signals to the post processors of this annotation that the result produced by the
	 * annotated {@link Supplier} has to be split. Specifics on how to split and what
	 * to split are left to the underlying framework.
	 *
	 * @return true if the resulting stream produced by the
	 * annotated {@link Supplier} has to be split.
	 */
	boolean splittable() default false;
}
