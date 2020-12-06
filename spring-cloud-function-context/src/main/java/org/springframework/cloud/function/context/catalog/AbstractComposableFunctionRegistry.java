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

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.config.RoutingFunction;
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
import org.springframework.util.Assert;
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
 * @author Dave Syer
 * @since 2.1
 *
 */
public abstract class AbstractComposableFunctionRegistry implements FunctionRegistry,
		ApplicationEventPublisherAware, EnvironmentAware {

	private final Map<String, Object> functions = new ConcurrentHashMap<>();

	private final Map<Object, String> names = new ConcurrentHashMap<>();

	private final Map<String, FunctionType> types = new ConcurrentHashMap<>();

	private Environment environment = new StandardEnvironment();

	protected ApplicationEventPublisher applicationEventPublisher;

	@SuppressWarnings("unchecked")
	@Override
	public <T> T lookup(Class<?> type, String name) {
		String functionDefinitionName = !StringUtils.hasText(name)
				&& this.environment.containsProperty("spring.cloud.function.definition")
						? this.environment.getProperty("spring.cloud.function.definition")
						: name;
		return (T) this.doLookup(type, functionDefinitionName);
	}

	@SuppressWarnings("serial")
	@Override
	public Set<String> getNames(Class<?> type) {
		if (type == null) {
			return new HashSet<String>(getSupplierNames()) {
				{
					addAll(getFunctionNames());
				}
			};
		}
		if (Supplier.class.isAssignableFrom(type)) {
			return this.getSupplierNames();
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
		return this.functions.entrySet().stream()
				.filter(entry -> entry.getValue() instanceof Supplier)
				.map(entry -> entry.getKey())
				.collect(Collectors.toSet());
	}

	/**
	 * Returns the names of available Functions.
	 * @return immutable {@link Set} of available {@link Function} names.
	 */
	public Set<String> getFunctionNames() {
		return this.functions.entrySet().stream()
				.filter(entry -> !(entry.getValue() instanceof Supplier))
				.map(entry -> entry.getKey())
				.collect(Collectors.toSet());
	}

	public boolean hasSuppliers() {
		return !CollectionUtils.isEmpty(getSupplierNames());
	}

	public boolean hasFunctions() {
		return !CollectionUtils.isEmpty(getFunctionNames());
	}

	/**
	 * The size of this catalog, which is the count of all Suppliers,
	 * Function and Consumers currently registered.
	 *
	 * @return the count of all Suppliers, Function and Consumers currently registered.
	 */
	@Override
	public int size() {
		return this.functions.size();
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


	public FunctionRegistration<?> getRegistration(Object function) {
		String functionName = function == null ? null
				: this.lookupFunctionName(function);
		if (StringUtils.hasText(functionName)) {
			FunctionRegistration<?> registration = new FunctionRegistration<Object>(
					function, functionName);
			FunctionType functionType = this.findType(registration, functionName);
			return registration.type(functionType.getType());
		}
		return null;
	}

	@Override
	public <T> void register(FunctionRegistration<T> functionRegistration) {
		Assert.notEmpty(functionRegistration.getNames(),
				"'registration' must contain at least one name before it is registered in catalog.");
		register(functionRegistration, functionRegistration.getNames().iterator().next());
	}



	/**
	 * Registers function wrapped by the provided FunctionRegistration with
	 * this FunctionRegistry.
	 *
	 * @param registration instance of {@link FunctionRegistration}
	 * @param key the name of the function
	 */
	protected void register(FunctionRegistration<?> registration, String key) {
		Object target = registration.getTarget();
		if (registration.getType() != null) {
			this.addType(key, registration.getType());
		}
		else {
			FunctionType functionType = findType(registration, key);
			if (functionType == null) {
				return; // TODO fixme
			}
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

	protected FunctionType findType(FunctionRegistration<?> functionRegistration, String name) {
		return functionRegistration.getType() != null
				?  functionRegistration.getType()
						:  this.getFunctionType(name);
	}


	protected void addSupplier(String name, Supplier<?> supplier) {
		this.functions.put(name, supplier);
	}

	protected void addFunction(String name, Function<?, ?> function) {
		this.functions.put(name, function);
	}

	protected void addType(String name, FunctionType functionType) {
		this.types.computeIfAbsent(name, str -> functionType);
	}

	protected void addName(Object function, String name) {
		this.names.put(function, name);
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

	private Object compose(String name, Map<String, Object> lookup) {

		name = name.replaceAll(",", "|").trim();
		Object composedFunction = null;

		if (lookup.containsKey(name)) {
			composedFunction = lookup.get(name);
		}
		else if (name.equals("") && lookup.size() >= 1 && lookup.size() <= 2) { // we may have RoutingFunction function
			String functionName = lookup.keySet().stream()
					.filter(fName -> !fName.equals(RoutingFunction.FUNCTION_NAME))
					.findFirst().orElseGet(() -> null);
			composedFunction = lookup.get(functionName);
		}
		else {
			String[] stages = StringUtils.delimitedListToStringArray(name, "|");

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
				if (composedFunction instanceof Function || composedFunction instanceof Consumer) {
					this.addFunction(name, (Function<?, ?>) composedFunction);
				}
				else if (composedFunction instanceof Supplier) {
					this.addSupplier(name, (Supplier<?>) composedFunction);
				}
			}

		}

		return composedFunction;
	}

	private FunctionRegistration<?> find(String name, boolean supplierFound) {
		Object result = this.functions.get(name);
		if (result == null && !StringUtils.hasText(name)) {
			if (supplierFound && this.getFunctionNames().size() == 1) {
				result = this.functions.get(this.getFunctionNames().iterator().next());
			}
			else if (!supplierFound && this.getSupplierNames().size() == 1) {
				result = this.functions.get(this.getSupplierNames().iterator().next());
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
//		if (a instanceof Supplier && b instanceof Function) {
//			Supplier<Flux<Object>> supplier = (Supplier<Flux<Object>>) a;
//			if (b instanceof FluxConsumer) {
//				if (supplier instanceof FluxSupplier) {
//					FluxConsumer<Object> fConsumer = ((FluxConsumer<Object>) b);
//					composedFunction = (Supplier<Mono<Void>>) () -> Mono.from(
//							supplier.get().compose(v -> fConsumer.apply(supplier.get())));
//				}
//				else {
//					throw new IllegalStateException(
//							"The provided supplier is finite (i.e., already composed with Consumer) "
//									+ "therefore it can not be composed with another consumer");
//				}
//			}
//			else {
//				Function<Object, Object> function = (Function<Object, Object>) b;
//				composedFunction = (Supplier<Object>) () -> function
//						.apply(supplier.get());
//			}
//		}
//		else
		if (a instanceof Function && b instanceof Function) {
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

	private Object doLookup(Class<?> type, String name) {
		Object function = this.compose(name, this.functions);
		if (function != null && type != null && !type.isAssignableFrom(function.getClass())) {
			function = null;
		}
		return function;
	}

}
