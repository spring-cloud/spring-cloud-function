/*
 * Copyright 2012-2017 the original author or authors.
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

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.util.Assert;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 *
 */
public class FunctionRegistration<T> {

	private T target;

	private final Set<String> names = new LinkedHashSet<>();

	private final Map<String, String> properties = new LinkedHashMap<>();

	private FunctionType type;

	public FunctionRegistration(T target) {
		Assert.notNull(target, "'target' must not be null");
		this.target = target;
	}

	public Map<String, String> getProperties() {
		return properties;
	}

	public Set<String> getNames() {
		return names;
	}

	public FunctionType getType() {
		return type;
	}

	/**
	 * Will set the names for this registration clearing all previous names first. If you
	 * want to add a name or set or names to the existing set of names use
	 * {@link #names(Collection)} or {@link #name(String)} or {@link #names(String...)}
	 * operations.
	 * @param names
	 */
	public void setNames(Set<String> names) {
		this.names.clear();
		this.names.addAll(names);
	}

	public T getTarget() {
		return target;
	}

	public FunctionRegistration<T> properties(Map<String, String> properties) {
		this.properties.putAll(properties);
		return this;
	}

	public FunctionRegistration<T> type(Type type) {
		this.type = FunctionType.of(type);
		return this;
	}

	/**
	 * Allows to override the target of this registration with a new target that typically
	 * wraps the original target. This typically happens when original target is wrapped
	 * into its {@link Flux} counterpart (e.g., Function into FluxFunction)
	 * @param target new target
	 * @return this registration with new target
	 */
	public FunctionRegistration<T> target(T target) {
		this.target = target;
		return this;
	}

	public FunctionRegistration<T> name(String name) {
		return this.names(name);
	}

	public FunctionRegistration<T> names(Collection<String> names) {
		this.names.addAll(names);
		return this;
	}

	public FunctionRegistration<T> names(String... names) {
		return this.names(Arrays.asList(names));
	}

}
