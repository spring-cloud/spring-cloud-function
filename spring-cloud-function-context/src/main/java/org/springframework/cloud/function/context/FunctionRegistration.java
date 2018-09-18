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

package org.springframework.cloud.function.context;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.cloud.function.core.FluxConsumer;
import org.springframework.cloud.function.core.FluxFunction;
import org.springframework.cloud.function.core.FluxSupplier;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class FunctionRegistration<T> implements BeanNameAware {

	private T target;

	private final Set<String> names = new LinkedHashSet<>();

	private final Map<String, String> properties = new LinkedHashMap<>();

	private FunctionType type;

	/**
	 * Creates instance of FunctionRegistration.
	 *
	 * @param target instance of {@link Supplier}, {@link Function} or {@link Consumer}
	 * @param names additional set of names for this registration. Additional names can be
	 * provided {@link #name(String)} or {@link #names(String...)} operations.
	 */
	public FunctionRegistration(T target, String... names) {
		Assert.notNull(target, "'target' must not be null");
		this.target = target;
		this.names(names);
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

	public FunctionRegistration<T> type(FunctionType type) {
		this.type = type;
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

	public <S> FunctionRegistration<S> wrap() {
		if (type == null || type.isWrapper()) {
			@SuppressWarnings("unchecked")
			FunctionRegistration<S> value = (FunctionRegistration<S>) this;
			return value;
		}
		@SuppressWarnings("unchecked")
		S target = (S) this.target;
		FunctionRegistration<S> result = new FunctionRegistration<S>(target);
		result.type(this.type.getType());
		if (target instanceof Function) {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			S wrapped = (S) new FluxFunction((Function) target);
			target = wrapped;
			result.type = result.type.wrap(Flux.class);
		}
		else if (target instanceof Supplier) {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			S wrapped = (S) new FluxSupplier((Supplier) target);
			target = wrapped;
			result.type = result.type.wrap(Flux.class);
		}
		else if (target instanceof Consumer) {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			S wrapped = (S) new FluxConsumer((Consumer) target);
			target = wrapped;
			result.type = result.type.wrap(Flux.class, Mono.class);
		}
		return result.target(target).names(this.names).properties(this.properties);
	}

	@Override
	public void setBeanName(String name) {
		if (CollectionUtils.isEmpty(this.names)) {
			this.name(name);
		}
	}

}
