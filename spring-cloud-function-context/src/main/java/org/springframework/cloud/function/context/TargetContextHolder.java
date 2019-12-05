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

/**
 * Holds the targetContext passed by the function runtime.
 *
 * @param <C> the type of the target specific (native) context object.
 *
 * @author Arno De Witte
 * @since 3.0.0
 */
public class TargetContextHolder<C> {

	private final ThreadLocal<C> threadLocal = new InheritableThreadLocal();

	public C getTargetContext() {
		return this.threadLocal.get();
	}

	public void setTargetContext(C targetContext) {
		this.threadLocal.set(targetContext);
	}

	public static <C> TargetContextHolder<C> of(C targetContext) {
		TargetContextHolder<C> targetContextHolder = new TargetContextHolder<>();
		targetContextHolder.setTargetContext(targetContext);
		return targetContextHolder;
	}
}
