/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.function.context;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Function;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.ComponentScan.Filter;

/**
 * Annotation that triggers scanning within the specified base packages for
 * any class that is assignable to {@link Function}. For each detected Function
 * class, a bean instance will be added to the context. The property key for
 * providing base packages is: {@code spring.cloud.function.scan.packages}
 * If no key is provided, the default base package is "functions".
 *
 * @author Mark Fisher
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@ComponentScan(basePackages = "${spring.cloud.function.scan.packages:functions}",
	includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Function.class))
public @interface FunctionScan {

}
