/*
 * Copyright 2012-2018 the original author or authors.
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

package org.springframework.cloud.function.context.test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ContextConfiguration;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@org.springframework.boot.test.context.SpringBootTest(properties = "spring.functional.enabled=true", webEnvironment = WebEnvironment.NONE)
@ContextConfiguration(loader = FunctionalTestContextLoader.class)
public @interface FunctionalSpringBootTest {

	@AliasFor(annotation=org.springframework.boot.test.context.SpringBootTest.class, attribute="properties")
	String[] value() default {};

	@AliasFor(annotation=org.springframework.boot.test.context.SpringBootTest.class, attribute="value")
	String[] properties() default {};

	@AliasFor(annotation=org.springframework.boot.test.context.SpringBootTest.class, attribute="classes")
	Class<?>[] classes() default {};

	@AliasFor(annotation=org.springframework.boot.test.context.SpringBootTest.class, attribute="webEnvironment")
	WebEnvironment webEnvironment() default WebEnvironment.MOCK;

}
