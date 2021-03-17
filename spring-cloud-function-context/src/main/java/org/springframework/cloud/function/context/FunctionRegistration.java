/*
 * Copyright 2016-2019 the original author or authors.
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

import net.jodah.typetools.TypeResolver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


import org.springframework.beans.factory.BeanNameAware;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.cloud.function.core.FluxConsumer;
import org.springframework.cloud.function.core.FluxFunction;
import org.springframework.cloud.function.core.FluxSupplier;
import org.springframework.cloud.function.core.FluxToMonoFunction;
import org.springframework.cloud.function.core.FluxedConsumer;
import org.springframework.cloud.function.core.FluxedFunction;
import org.springframework.cloud.function.core.MonoSupplier;
import org.springframework.cloud.function.core.MonoToFluxFunction;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;



/**
 * @param <T> target type
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class FunctionRegistration<T> implements BeanNameAware {

	/**
	 * Suffix used to add to the name of FunctionRegistration bean that
	 * corresponds to the an actual function bean. It is often used when
	 * the actual function bean may not be a java Function (e.g., Kotlin)
	 * and certain custom wrapping is required.
	 * <br>
	 * NOTE: This is not intended as oublis API
	 */
	public static String REGISTRATION_NAME_SUFFIX = "_registration";

	private final Set<String> names = new LinkedHashSet<>();

	private final Map<String, String> properties = new LinkedHashMap<>();

	private T target;

	private FunctionType type;

	/**
	 * Creates instance of FunctionRegistration.
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
		return this.properties;
	}

	public Set<String> getNames() {
		return this.names;
	}

	/**
	 * Will set the names for this registration clearing all previous names first. If you
	 * want to add a name or set or names to the existing set of names use
	 * {@link #names(Collection)} or {@link #name(String)} or {@link #names(String...)}
	 * operations.
	 * @param names - bean names
	 */
	public void setNames(Set<String> names) {
		this.names.clear();
		this.names.addAll(names);
	}

	public FunctionType getType() {
		return this.type;
	}

	public T getTarget() {
		return this.target;
	}

	public FunctionRegistration<T> properties(Map<String, String> properties) {
		this.properties.putAll(properties);
		return this;
	}

	public FunctionRegistration<T> type(Type type) {
		return type(FunctionType.of(type));
	}

	public FunctionRegistration<T> type(FunctionType type) {

		Type t = FunctionTypeUtils.discoverFunctionTypeFromClass(this.target.getClass());
		if (t == null) { // only valid for Kafka Stream KStream[] return type.
			return null;
		}
		FunctionType discoveredFunctionType = FunctionType.of(t);
		Class<?> inputType = TypeResolver.resolveRawClass(discoveredFunctionType.getInputType(), null);
		Class<?> outputType = TypeResolver.resolveRawClass(discoveredFunctionType.getOutputType(), null);

		if (!(inputType.isAssignableFrom(TypeResolver.resolveRawClass(type.getInputType(), null))
				&& outputType.isAssignableFrom(TypeResolver.resolveRawClass(type.getOutputType(), null)))) {
			throw new IllegalStateException("Discovered function type does not match provided function type. Discovered: "
					+ discoveredFunctionType + "; Provided: " + type);
		}
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

	/**
	 * Transforms (wraps) function identified by the 'target' to its {@code Flux}
	 * equivalent unless it already is. For example, {@code Function<String, String>}
	 * becomes {@code Function<Flux<String>, Flux<String>>}
	 * @param <S> the expected target type of the function (e.g., FluxFunction)
	 * @return {@code FunctionRegistration} with the appropriately wrapped target.
	 *
	 */

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public <S> FunctionRegistration<S> wrap() {
		this.isFunctionSignatureSupported();
		FunctionRegistration<S> result;
		if (this.type == null) {
			result = (FunctionRegistration<S>) this;
		}
		else if (this.target instanceof RoutingFunction) {
			S target = (S) this.target;
			result = new FunctionRegistration<S>(target);
			result.type(this.type.getType());
			result = result.target(target).names(this.names)
					.type(result.type.wrap(Flux.class)).properties(this.properties);
		}
		else {
			S target = (S) this.target;
			result = new FunctionRegistration<S>(target);
			result.type(this.type.getType());

			if (!this.type.isWrapper()) {
				target = target instanceof Supplier
						? (S) new FluxSupplier((Supplier<?>) target)
						: target instanceof Function
								? (S) new FluxFunction((Function<?, ?>) target)
								: (S) new FluxConsumer((Consumer<?>) target);
			}
			else if (Mono.class.isAssignableFrom(this.type.getOutputWrapper())) {
				target = target instanceof Supplier
						? (S) new MonoSupplier((Supplier<?>) target)
						: (S) new FluxToMonoFunction((Function<?, ?>) target);
			}
			else if (Mono.class.isAssignableFrom(this.type.getInputWrapper())) {
				target = (S) new MonoToFluxFunction((Function) target);
			}
			else if (target instanceof Consumer) {
				target = (S) new FluxedConsumer((Consumer<?>) target);
			}
			else if (target instanceof Function) {
				target = (S) new FluxedFunction((Function<?, ?>) target);
			}
			result = result.target(target).names(this.names)
					.type(result.type.wrap(Flux.class)).properties(this.properties);
		}

		return result;
	}

	@Override
	public void setBeanName(String name) {
		if (CollectionUtils.isEmpty(this.names)) {
			this.name(name);
		}
	}

	private void isFunctionSignatureSupported() {
		if (type != null) {
			Assert.isTrue(!(Mono.class.isAssignableFrom(this.type.getOutputWrapper())
					&& Mono.class.isAssignableFrom(this.type.getInputWrapper())),
					"Function<Mono, Mono> is not supported.");
		}
	}

}
