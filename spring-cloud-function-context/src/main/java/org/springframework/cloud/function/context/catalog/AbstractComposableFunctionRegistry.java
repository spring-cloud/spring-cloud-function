/*
 * Copyright 2019-2019 the original author or authors.
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

package org.springframework.cloud.function.context.catalog;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.core.FluxConsumer;
import org.springframework.cloud.function.core.FluxSupplier;
import org.springframework.cloud.function.core.FluxToMonoFunction;
import org.springframework.cloud.function.core.IsolatedConsumer;
import org.springframework.cloud.function.core.IsolatedFunction;
import org.springframework.cloud.function.core.IsolatedSupplier;
import org.springframework.cloud.function.core.MonoToFluxFunction;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Base implementation of {@link FunctionRegistry} which supports function composition
 * during lookups. For example if this registry contains function 'a' and 'b' you can
 * compose them into a single function by simply piping two names together during the
 * lookup {@code this.lookup(Function.class, "a|b")}.
 *
 * Comma ',' is also supported as composition delimiter (e.g., {@code "a,b"}).
 *
 * @author Oleg Zhurakousky
 * @since 2.1
 *
 */
public abstract class AbstractComposableFunctionRegistry implements FunctionRegistry,
		FunctionInspector, ApplicationEventPublisherAware, EnvironmentAware {

	private final Map<String, Object> suppliers = new ConcurrentHashMap<>();

	private final Map<String, Object> functions = new ConcurrentHashMap<>();

	private final Map<String, Object> consumers = new ConcurrentHashMap<>();

	private final Map<Object, String> names = new ConcurrentHashMap<>();

	private final Map<String, FunctionType> types = new ConcurrentHashMap<>();

	private Environment environment = new StandardEnvironment();

	protected ApplicationEventPublisher applicationEventPublisher;

	@Override
	public <T> T lookup(Class<?> type, String name) {
		String functionDefinitionName = !StringUtils.hasText(name)
				&& this.environment.containsProperty("spring.cloud.function.definition")
						? this.environment.getProperty("spring.cloud.function.definition")
						: name;
		return this.doLookup(type, functionDefinitionName);
	}

	@SuppressWarnings("serial")
	@Override
	public Set<String> getNames(Class<?> type) {
		if (type == null) {
			return new HashSet<String>(getSupplierNames()) {
				{
					addAll(getConsumerNames());
					addAll(getFunctionNames());
				}
			};
		}
		if (Supplier.class.isAssignableFrom(type)) {
			return this.getSupplierNames();
		}
		if (Consumer.class.isAssignableFrom(type)) {
			return this.getConsumerNames();
		}
		if (Function.class.isAssignableFrom(type)) {
			return this.getFunctionNames();
		}
		return Collections.emptySet();
	}

	/**
	 * Returns the names of available Suppliers.
	 * @return immutable {@link Set} of available {@link Supplier} names.
	 */
	public Set<String> getSupplierNames() {
		return Collections.unmodifiableSet(this.suppliers.keySet());
	}

	/**
	 * Returns the names of available Functions.
	 * @return immutable {@link Set} of available {@link Function} names.
	 */
	public Set<String> getFunctionNames() {
		return this.functions.keySet();
	}

	/**
	 * Returns the names of available Consumers.
	 * @return immutable {@link Set} of available {@link Consumer} names.
	 */
	public Set<String> getConsumerNames() {
		return this.consumers.keySet();
	}

	public boolean hasSuppliers() {
		return !CollectionUtils.isEmpty(getSupplierNames());
	}

	public boolean hasFunctions() {
		return !CollectionUtils.isEmpty(getFunctionNames());
	}

	public boolean hasConsumers() {
		return !CollectionUtils.isEmpty(getConsumerNames());
	}

	/**
	 * The count of all Suppliers, Function and Consumers currently registered.
	 * @return the count of all Suppliers, Function and Consumers currently registered.
	 */
	@Override
	public int size() {
		return getSupplierNames().size() + getFunctionNames().size()
				+ getConsumerNames().size();
	}

	public FunctionType getFunctionType(String name) {
		return this.types.get(name);
	}

	/**
	 * A reverse lookup where one can determine the actual name of the function reference.
	 * @param function should be an instance of {@link Supplier}, {@link Function} or
	 * {@link Consumer};
	 * @return the name of the function or null.
	 */
	public String lookupFunctionName(Object function) {
		return this.names.containsKey(function) ? this.names.get(function) : null;
	}

	@Override
	public void setApplicationEventPublisher(
			ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	protected void wrap(FunctionRegistration<?> registration, String key) {
		Object target = registration.getTarget();
		this.addName(target, key);
		if (registration.getType() != null) {
			this.addType(key, registration.getType());
		}
		else {
			FunctionType functionType = findType(registration);
			this.addType(key, functionType);
			registration.type(functionType.getType());
		}
		Class<?> type;
		registration = isolated(registration).wrap();
		target = registration.getTarget();
		if (target instanceof Supplier) {
			type = Supplier.class;
			for (String name : registration.getNames()) {
				this.addSupplier(name, (Supplier<?>) registration.getTarget());
			}
		}
		else if (target instanceof Consumer) {
			type = Consumer.class;
			for (String name : registration.getNames()) {
				this.addConsumer(name, (Consumer<?>) registration.getTarget());
			}
		}
		else if (target instanceof Function) {
			type = Function.class;
			for (String name : registration.getNames()) {
				this.addFunction(name, (Function<?, ?>) registration.getTarget());
			}
		}
		else {
			return;
		}
		this.addName(registration.getTarget(), key);
		if (this.applicationEventPublisher != null) {
			this.applicationEventPublisher.publishEvent(new FunctionRegistrationEvent(
					registration.getTarget(), type, registration.getNames()));
		}
	}

	protected FunctionType findType(FunctionRegistration<?> functionRegistration) {
		FunctionType functionType = functionRegistration.getType();
		if (functionType != null) {
			return functionType;
		}
		throw new IllegalStateException(
				"Unless FunctionType is already available in FunctionRegistration, "
						+ "this operation must be overriden "
						+ "by the implementation of the FunctionRegistry.");
	}

	protected void addSupplier(String name, Supplier<?> supplier) {
		this.suppliers.put(name, supplier);
	}

	protected void addFunction(String name, Function<?, ?> function) {
		this.functions.put(name, function);
	}

	protected void addConsumer(String name, Consumer<?> consumer) {
		this.consumers.put(name, consumer);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object doLookup(String name, Map lookup, Class<?> typeOfFunction) {
		Object function = compose(name, lookup);
		if (function != null && typeOfFunction.isAssignableFrom(function.getClass())) {
			return function;
		}
		return null;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private FunctionRegistration<?> isolated(FunctionRegistration<?> input) {
		FunctionRegistration<Object> registration = (FunctionRegistration<Object>) input;
		Object target = registration.getTarget();
		boolean isolated = getClass().getClassLoader() != target.getClass()
				.getClassLoader();
		if (isolated) {
			if (target instanceof Supplier<?> && isolated) {
				target = new IsolatedSupplier((Supplier<?>) target);
			}
			else if (target instanceof Function<?, ?>) {
				target = new IsolatedFunction((Function<?, ?>) target);
			}
			else if (target instanceof Consumer<?>) {
				target = new IsolatedConsumer((Consumer<?>) target);
			}
		}

		registration.target(target);
		return registration;
	}

	protected void addType(String name, FunctionType functionType) {
		this.types.computeIfAbsent(name, str -> functionType);
	}

	protected void addName(Object function, String name) {
		this.names.put(function, name);
	}

	private Supplier<?> lookupSupplier(String name) {
		return (Supplier<?>) doLookup(name, this.suppliers, Supplier.class);
	}

	private Function<?, ?> lookupFunction(String name) {
		return (Function<?, ?>) doLookup(name, this.functions, Function.class);
	}

	private Consumer<?> lookupConsumer(String name) {
		return (Consumer<?>) doLookup(name, this.consumers, Consumer.class);
	}

	private Object compose(String name, Map<String, Object> lookup) {

		name = normalizeName(name);
		Object composedFunction = null;

		if (lookup.containsKey(name)) {
			composedFunction = lookup.get(name);
		}
		else if (name.equals("") && lookup.size() == 1) {
			composedFunction = lookup.values().iterator().next();
		}
		else {
			String[] stages = StringUtils.delimitedListToStringArray(name, "|");
			if (Stream.of(stages).allMatch(funcName -> contains(funcName))) {

				AtomicBoolean supplierPresent = new AtomicBoolean();
				List<FunctionRegistration<?>> composableFunctions = Stream.of(stages)
						.map(funcName -> find(funcName, supplierPresent.get()))
						.filter(x -> x != null)
						.peek(f -> supplierPresent.set(f.getTarget() instanceof Supplier))
						.collect(Collectors.toList());
				FunctionRegistration<?> composedRegistration = composableFunctions
						.stream().reduce((a, z) -> composeFunctions(a, z))
						.orElseGet(() -> null);

				if (composedRegistration != null
						&& composedRegistration.getTarget() != null
						&& !this.types.containsKey(name)) {

					composedFunction = composedRegistration.getTarget();
					this.addType(name, composedRegistration.getType());
					this.addName(composedFunction, name);
					if (composedFunction instanceof Function) {
						this.addFunction(name, (Function<?, ?>) composedFunction);
					}
					else if (composedFunction instanceof Consumer) {
						this.addConsumer(name, (Consumer<?>) composedFunction);
					}
					else if (composedFunction instanceof Supplier) {
						this.addSupplier(name, (Supplier<?>) composedFunction);

					}
				}

			}
		}

		return composedFunction;
	}

	private String normalizeName(String name) {
		return name.replaceAll(",", "|").trim();
	}

	private boolean contains(String name) {
		if (!StringUtils.hasText(name)) {
			return true;
		}
		return getSupplierNames().contains(name) || getFunctionNames().contains(name)
				|| getConsumerNames().contains(name);
	}

	private FunctionRegistration<?> find(String name, boolean supplierFound) {
		Object result = this.suppliers.get(name);
		if (result == null) {
			result = this.functions.get(name);
		}
		if (result == null) {
			result = this.consumers.get(name);
		}
		if (result == null && !StringUtils.hasText(name)) {
			if (supplierFound && this.functions.size() == 1) {
				result = this.functions.values().iterator().next();
			}
			else if (!supplierFound && this.suppliers.size() == 1) {
				result = this.suppliers.values().iterator().next();
			}
		}

		return getRegistration(result);
	}

	@SuppressWarnings("unchecked")
	private FunctionRegistration<?> composeFunctions(FunctionRegistration<?> aReg,
			FunctionRegistration<?> bReg) {
		FunctionType aType = aReg.getType();
		FunctionType bType = bReg.getType();
		Object a = aReg.getTarget();
		Object b = bReg.getTarget();
		if (aType != null && bType != null) {
			if (aType.isMessage() && !bType.isMessage()) {
				bType = bType.message();
				b = message(b);
			}
		}
		Object composedFunction = null;
		if (a instanceof Supplier && b instanceof Function) {
			Supplier<Flux<Object>> supplier = (Supplier<Flux<Object>>) a;
			if (b instanceof FluxConsumer) {
				if (supplier instanceof FluxSupplier) {
					FluxConsumer<Object> fConsumer = ((FluxConsumer<Object>) b);
					composedFunction = (Supplier<Mono<Void>>) () -> Mono.from(
							supplier.get().compose(v -> fConsumer.apply(supplier.get())));
				}
				else {
					throw new IllegalStateException(
							"The provided supplier is finite (i.e., already composed with Consumer) "
									+ "therefore it can not be composed with another consumer");
				}
			}
			else {
				Function<Object, Object> function = (Function<Object, Object>) b;
				composedFunction = (Supplier<Object>) () -> function
						.apply(supplier.get());
			}
		}
		else if (a instanceof Function && b instanceof Function) {
			Function<Object, Object> function1 = (Function<Object, Object>) a;
			Function<Object, Object> function2 = (Function<Object, Object>) b;
			if (function1 instanceof FluxToMonoFunction) {
				if (function2 instanceof MonoToFluxFunction) {
					composedFunction = function1.andThen(function2);
				}
				else {
					throw new IllegalStateException(
							"The provided function is finite (i.e., returns Mono<?>) "
									+ "therefore it can *only* be composed with compatible function (i.e., Function<Mono, Flux>");
				}
			}
			else if (function2 instanceof FluxToMonoFunction) {
				composedFunction = new FluxToMonoFunction<Object, Object>(
						((Function<Flux<Object>, Flux<Object>>) a).andThen(
								((FluxToMonoFunction<Object, Object>) b).getTarget()));
			}
			else {
				composedFunction = function1.andThen(function2);
			}
		}
		else if (a instanceof Function && b instanceof Consumer) {
			Function<Object, Object> function = (Function<Object, Object>) a;
			Consumer<Object> consumer = (Consumer<Object>) b;
			composedFunction = (Consumer<Object>) v -> consumer.accept(function.apply(v));
		}
		else {
			throw new IllegalArgumentException(String
					.format("Could not compose %s and %s", a.getClass(), b.getClass()));
		}
		String name = aReg.getNames().iterator().next() + "|"
				+ bReg.getNames().iterator().next();
		return new FunctionRegistration<>(composedFunction, name)
				.type(FunctionType.compose(aType, bType));
	}

	private Object message(Object input) {
		if (input instanceof Supplier) {
			return new MessageSupplier((Supplier<?>) input);
		}
		if (input instanceof Consumer) {
			return new MessageConsumer((Consumer<?>) input);
		}
		if (input instanceof Function) {
			return new MessageFunction((Function<?, ?>) input);
		}
		return input;
	}

	@SuppressWarnings("unchecked")
	private <T> T doLookup(Class<?> type, String name) {
		T function = null;
		if (type == null) {
			function = (T) this.lookupFunction(name);
			if (function == null) {
				function = (T) this.lookupConsumer(name);
			}
			if (function == null) {
				function = (T) this.lookupSupplier(name);
			}
		}
		else if (Function.class.isAssignableFrom(type)) {
			function = (T) this.lookupFunction(name);
		}
		else if (Supplier.class.isAssignableFrom(type)) {
			function = (T) this.lookupSupplier(name);
		}
		else if (Consumer.class.isAssignableFrom(type)) {
			function = (T) this.lookupConsumer(name);
		}
		return function;
	}

}
