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
 * A marker and qualifier annotation to signal that
 * annotated functional factory method is a bean (e.g., Supplier, Function or Consumer)
 * that also needs to be polled periodically.
 * <br>
 * This has special significance to the reactive suppliers (e.g., {@code Supplier<Flux<?>>}),
 * since by default they are treated as producers of an infinite stream.
 * However if such suppliers produce a finite stream they may need to be triggered again.
 * <br>
 * <br>
 * NOTE: The spring-cloud-function framework provides no default post processing behavior for this annotation. This
 * means that annotating a factory method with this annotation will not have any effect without some application/framework
 * specific post processing (see spring-cloud-stream as an example).
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
public @interface PollableBean {

	/**
	 * Signals to the post processors of this annotation that the result produced by the
	 * annotated {@link Supplier} has to be split. Specifics on how to split and what
	 * to split are left to the underlying framework.
	 *
	 * @return true if the resulting stream produced by the
	 * annotated {@link Supplier} has to be split.
	 */
	boolean splittable() default true;
}
