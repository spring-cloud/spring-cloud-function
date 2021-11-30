/*
 * Copyright 2019-2021 the original author or authors.
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


import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.ReflectionUtils;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class BeanFactoryAwareFunctionRegistryTests {

	private ApplicationContext context;

	private FunctionCatalog configureCatalog() {
		return this.configureCatalog(SampleFunctionConfiguration.class);
	}

	private FunctionCatalog configureCatalog(Class<?>... configClass) {
		this.context = new SpringApplicationBuilder(configClass)
				.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true");
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		return catalog;
	}

	@BeforeEach
	public void before() {
		System.clearProperty("spring.cloud.function.definition");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testDefaultLookup() throws Exception {
		FunctionCatalog catalog = this.configureCatalog();
		Object function = catalog.lookup("");
		assertThat(function).isNull();
		//==
		System.setProperty("spring.cloud.function.definition", "uppercase");
		catalog = this.configureCatalog();
		function = catalog.lookup("");
		assertThat(function).isNotNull();
//		Field field = ReflectionUtils.findField(FunctionInvocationWrapper.class, "composed");
//		field.setAccessible(true);
		assertThat(((FunctionInvocationWrapper) function).isComposed()).isFalse();
		//==
		System.setProperty("spring.cloud.function.definition", "uppercase|uppercaseFlux");
		catalog = this.configureCatalog();
//		function = catalog.lookup("", "application/json");
		function = catalog.lookup("");
		Function<Flux<String>, Flux<Message<String>>> typedFunction = (Function<Flux<String>, Flux<Message<String>>>) function;
		Object blockFirst = typedFunction.apply(Flux.just("hello")).blockFirst();
		System.out.println(blockFirst);
		assertThat(function).isNotNull();
//		field = ReflectionUtils.findField(FunctionInvocationWrapper.class, "composed");
//		field.setAccessible(true);
//		assertThat(((boolean) field.get(function))).isTrue();
		assertThat(((FunctionInvocationWrapper) function).isComposed()).isTrue();
	}

	@Test
	public void testImperativeFunction() {
		FunctionCatalog catalog = this.configureCatalog();

//		Function<String, String> asIs = catalog.lookup("uppercase");
//		assertThat(asIs.apply("uppercase")).isEqualTo("UPPERCASE");
//
//		Function<Flux<String>, Flux<String>> asFlux = catalog.lookup("uppercase");
//		List<String> result = asFlux.apply(Flux.just("uppercaseFlux", "uppercaseFlux2")).collectList().block();
//		assertThat(result.get(0)).isEqualTo("UPPERCASEFLUX");
//		assertThat(result.get(1)).isEqualTo("UPPERCASEFLUX2");

		Function<Flux<Message<byte[]>>, Flux<Message<byte[]>>> messageFlux = catalog.lookup("uppercase", "application/json");
		Message<byte[]> message1 = MessageBuilder.withPayload("\"uppercaseFlux\"".getBytes()).setHeader(MessageHeaders.CONTENT_TYPE, "application/json").build();
		Message<byte[]> message2 = MessageBuilder.withPayload("\"uppercaseFlux2\"".getBytes()).setHeader(MessageHeaders.CONTENT_TYPE, "application/json").build();
		List<Message<byte[]>> messageResult = messageFlux.apply(Flux.just(message1, message2)).collectList().block();
		assertThat(messageResult.get(0).getPayload()).isEqualTo("\"UPPERCASEFLUX\"".getBytes(StandardCharsets.UTF_8));
		assertThat(messageResult.get(1).getPayload()).isEqualTo("\"UPPERCASEFLUX2\"".getBytes(StandardCharsets.UTF_8));
	}

	@Test
	public void testConsumerFunction() { // function that returns Void, effectively a Consumer
		FunctionCatalog catalog = this.configureCatalog();

		Function<String, Void> consumerFunction = catalog.lookup("consumerFunction");
		assertThat(consumerFunction.apply("hello")).isNull();

		Function<Message<byte[]>, Void> consumerFunctionAsMessageA = catalog.lookup("consumerFunction");
		assertThat(consumerFunctionAsMessageA.apply(new GenericMessage<byte[]>("\"hello\"".getBytes()))).isNull();

		Function<Message<byte[]>, Void> consumerFunctionAsMessageB = catalog.lookup("consumerFunction", "application/json");
		assertThat(consumerFunctionAsMessageB.apply(new GenericMessage<byte[]>("\"hello\"".getBytes()))).isNull();
	}

	@Test
	public void testMessageToPojoConversion() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<Message<String>, Person> uppercasePerson = catalog.lookup("uppercasePerson");
		Person person =  uppercasePerson.apply(MessageBuilder.withPayload("{\"name\":\"bill\",\"id\":2}").build());
		assertThat(person.getName()).isEqualTo("BILL");
	}

	/*
	 * When invoking imperative function as reactive the rules are
	 * - the input wrapper must match the output wrapper (e.g., <Flux, Flux> or <Mono, Mono>)
	 */
	@Test
	public void testImperativeVoidInputFunction() {
		FunctionCatalog catalog = this.configureCatalog();

		Function<String, String> anyInputSignature = catalog.lookup("voidInputFunction");
		assertThat(anyInputSignature.apply(null)).isEqualTo("voidInputFunction");

		Function<Void, String> asVoid = catalog.lookup("voidInputFunction");
		assertThat(asVoid.apply(null)).isEqualTo("voidInputFunction");
	}

	@Test
	public void testReactiveVoidInputFunction() {
		FunctionCatalog catalog = this.configureCatalog();

		Function<Flux<Void>, Flux<String>> voidInputFunctionReactive = catalog.lookup("voidInputFunctionReactive");
		List<String> resultList = voidInputFunctionReactive.apply(Flux.empty()).collectList().block();
		assertThat(resultList.get(0)).isEqualTo("voidInputFunctionReactive");

		Function<Void, Flux<String>> asVoid = catalog.lookup("voidInputFunctionReactive");
		resultList = asVoid.apply(null).collectList().block();
		assertThat(resultList.get(0)).isEqualTo("voidInputFunctionReactive");
	}

	@Test
	public void testReactiveVoidInputFunctionAsSupplier() {
		FunctionCatalog catalog = this.configureCatalog();
		Supplier<Flux<String>> functionAsSupplier = catalog.lookup("voidInputFunctionReactive");
		List<String> resultList = functionAsSupplier.get().collectList().block();
		assertThat(resultList.get(0)).isEqualTo("voidInputFunctionReactive");

		Supplier<Flux<String>> functionAsSupplier2 = catalog.lookup("voidInputFunctionReactive2");
		resultList = functionAsSupplier2.get().collectList().block();
		assertThat(resultList.get(0)).isEqualTo("voidInputFunctionReactive2");
	}


	@Test
	public void testComposition() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<Flux<String>, Flux<String>> fluxFunction = catalog.lookup("uppercase|reverseFlux");

		List<String> result = fluxFunction.apply(Flux.just("hello", "bye")).collectList().block();
		assertThat(result.get(0)).isEqualTo("OLLEH");
		assertThat(result.get(1)).isEqualTo("EYB");

		fluxFunction = catalog.lookup("uppercase|reverse|reverseFlux");
		result = fluxFunction.apply(Flux.just("hello", "bye")).collectList().block();
		assertThat(result.get(0)).isEqualTo("HELLO");
		assertThat(result.get(1)).isEqualTo("BYE");

		fluxFunction = catalog.lookup("uppercase|reverseFlux|reverse");
		result = fluxFunction.apply(Flux.just("hello", "bye")).collectList().block();
		assertThat(result.get(0)).isEqualTo("HELLO");
		assertThat(result.get(1)).isEqualTo("BYE");

		fluxFunction = catalog.lookup("uppercase|reverse");
		result = fluxFunction.apply(Flux.just("hello", "bye")).collectList().block();
		assertThat(result.get(0)).isEqualTo("OLLEH");
		assertThat(result.get(1)).isEqualTo("EYB");

		Function<String, String> function = catalog.lookup("uppercase|reverse");
		assertThat(function.apply("foo")).isEqualTo("OOF");
	}

	@Test
	public void testCompositionSupplierAndFunction() {
		FunctionCatalog catalog = this.configureCatalog();

		Supplier<Flux<String>> numberSupplierFlux = catalog.lookup("numberword|uppercaseFlux");
		String result = numberSupplierFlux.get().blockFirst();
		assertThat(result).isEqualTo("ONE");
	}

	/*
	 * This test should fail since the actual function is <Flux, Flux>, hence we can
	 * not possibly convert Flux (which implies "many") to a single string.
	 * Further more, such flux will need to be triggered (e.g., subscribe(..) )
	 */
	@SuppressWarnings("unused")
	@Test
	public void testReactiveFunctionWithImperativeInputAndOutputFail() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<String, String> reverse = catalog.lookup("reverseFlux");
		Assertions.assertThrows(ClassCastException.class, () -> {
			String result = reverse.apply("reverseFlux");
		});
	}

	@Test
	public void testCompositionWithOutputConversion() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<Flux<String>, Flux<Message<byte[]>>> fluxFunction = catalog.lookup("uppercase|reverseFlux", "application/json");
		List<Message<byte[]>> result = fluxFunction.apply(Flux.just("hello", "bye")).collectList().block();
		assertThat(result.get(0).getPayload()).isEqualTo("\"OLLEH\"".getBytes());
		assertThat(result.get(1).getPayload()).isEqualTo("\"EYB\"".getBytes());

		fluxFunction = catalog.lookup("uppercase|reverse|reverseFlux", "application/json");
		result = fluxFunction.apply(Flux.just("hello", "bye")).collectList().block();
		assertThat(result.get(0).getPayload()).isEqualTo("\"HELLO\"".getBytes());
		assertThat(result.get(1).getPayload()).isEqualTo("\"BYE\"".getBytes());

		fluxFunction = catalog.lookup("uppercase|reverseFlux|reverse", "application/json");
		result = fluxFunction.apply(Flux.just("hello", "bye")).collectList().block();
		assertThat(result.get(0).getPayload()).isEqualTo("\"HELLO\"".getBytes());
		assertThat(result.get(1).getPayload()).isEqualTo("\"BYE\"".getBytes());
	}

	@Test
	public void testReactiveFunctionWithImperativeInputReactiveOutput() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<String, Flux<String>> reverse = catalog.lookup("reverseFlux");
		List<String> result = reverse.apply("reverse").collectList().block();
		assertThat(result.size()).isEqualTo(1);
		assertThat(result.get(0)).isEqualTo("esrever");
	}

	@Test
	public void testMonoVoidToMonoVoid() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<Mono<Void>, Mono<Void>> monoToMono = catalog.lookup("monoVoidToMonoVoid");
		Void block = monoToMono.apply(Mono.empty()).block();
		assertThat(block).isNull();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void textTypeConversionWithComplexInputType() {
		FunctionCatalog catalog = this.configureCatalog(ComplexTypeFunctionConfiguration.class);
		Function function = catalog.lookup("function");

		// as String
		String result = (String) function.apply("{\"key\":\"purchase\",\"data\":{\"name\":\"bike\"}}");
		assertThat(result).isEqualTo("BIKE");

		// as byte[]
		result = (String) function.apply("{\"key\":\"purchase\",\"data\":{\"name\":\"bike\"}}".getBytes());
		assertThat(result).isEqualTo("BIKE");

		// as Message<String>
		result = (String) function.apply(MessageBuilder.withPayload("{\"key\":\"purchase\",\"data\":{\"name\":\"bike\"}}").build());
		assertThat(result).isEqualTo("BIKE");

		// as Message<BYTE[]>
		result = (String) function.apply(MessageBuilder.withPayload("{\"key\":\"purchase\",\"data\":{\"name\":\"bike\"}}".getBytes()).build());
		assertThat(result).isEqualTo("BIKE");
	}

	// MULTI INPUT/OUTPUT


	@Test
	public void testMultiInput() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<Tuple2<Flux<String>, Flux<Integer>>, Flux<String>> multiInputFunction =
									catalog.lookup("multiInputSingleOutputViaReactiveTuple");
		Flux<String> stringStream = Flux.just("one", "two", "three");
		Flux<Integer> intStream = Flux.just(1, 2, 3);

		List<String> result = multiInputFunction.apply(Tuples.of(stringStream, intStream)).collectList().block();
		assertThat(result.size()).isEqualTo(3);
		assertThat(result.get(0)).isEqualTo("one-1");
		assertThat(result.get(1)).isEqualTo("two-2");
		assertThat(result.get(2)).isEqualTo("three-3");
	}


	//@Test
	public void testMultiInputWithComposition() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<Tuple2<Flux<String>, Flux<String>>, Flux<String>> multiInputFunction =
									catalog.lookup("multiInputSingleOutputViaReactiveTuple|uppercase");
		Flux<String> stringStream = Flux.just("one", "two", "three");
		Flux<String> intStream = Flux.just("1", "2", "3");

		List<String> result = multiInputFunction.apply(Tuples.of(stringStream, intStream)).collectList().block();
		assertThat(result.size()).isEqualTo(3);
		assertThat(result.get(0)).isEqualTo("ONE-1");
		assertThat(result.get(1)).isEqualTo("TWO-2");
		assertThat(result.get(2)).isEqualTo("THREE-3");
	}


	@Test
	public void testMultiOutput() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<Flux<Person>, Tuple3<Flux<Person>, Flux<String>, Flux<Integer>>> multiOutputFunction =
									catalog.lookup("multiOutputAsTuple");
		Flux<Person> personStream = Flux.just(new Person("Uncle Sam", 1), new Person("Oncle Pierre", 2));

		Tuple3<Flux<Person>, Flux<String>, Flux<Integer>> result = multiOutputFunction.apply(personStream);

		result.getT1().subscribe(v -> System.out.println("=> 1: " + v));
		result.getT2().subscribe(v -> System.out.println("=> 2: " + v));
		result.getT3().subscribe(v -> System.out.println("=> 3: " + v));
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void SCF_GH_409ConfigurationTests() {
		FunctionCatalog catalog = this.configureCatalog(SCF_GH_409ConfigurationAsSupplier.class);
		assertThat((Function) catalog.lookup("")).isNull();

		catalog = this.configureCatalog(SCF_GH_409ConfigurationAsFunction.class);
		assertThat((Function) catalog.lookup("")).isNull();
	}

	@Test
	public void pojoFunctionAsJson() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<String, Person> uppercasePerson = catalog.lookup("uppercasePerson");

		Person person = uppercasePerson.apply("{\"name\":\"bill\",\"id\":2}");
		assertThat(person.getName()).isEqualTo("BILL");
	}

	@Test
	public void SCF_GH_429ConfigurationTests() throws Exception {
		FunctionCatalog catalog = this.configureCatalog(MyFunction.class);
		FunctionInvocationWrapper function = catalog.lookup("beanFactoryAwareFunctionRegistryTests.MyFunction");
		assertThat(function).isNotNull();
		Field f = ReflectionUtils.findField(FunctionInvocationWrapper.class, "composed");
		f.setAccessible(true);
		boolean composed = (boolean) f.get(function);
		assertThat(composed).isFalse();
	}

	@SuppressWarnings("unchecked")
	@Test
	@Disabled
	public void byteArrayNoSpecialHandling() throws Exception {
		FunctionCatalog catalog = this.configureCatalog(ByteArrayFunction.class);
		FunctionInvocationWrapper function = catalog.lookup("beanFactoryAwareFunctionRegistryTests.ByteArrayFunction", "application/json");
		assertThat(function).isNotNull();
		Message<byte[]> result = (Message<byte[]>) function.apply(MessageBuilder.withPayload("hello".getBytes()).setHeader(MessageHeaders.CONTENT_TYPE, "application/octet-stream").build());

		System.out.println(new String(result.getPayload()));

		assertThat(result.getPayload()).isEqualTo("\"b2xsZWg=\"".getBytes());
	}

	@Test
	public void testMultipleValuesInOutputHandling() throws Exception {
		FunctionCatalog catalog = this.configureCatalog(CollectionOutConfiguration.class);
		FunctionInvocationWrapper function = catalog.lookup("parseToList", "application/json");
		assertThat(function).isNotNull();
		Object result = function.apply(MessageBuilder.withPayload("1, 2, 3".getBytes()).setHeader(MessageHeaders.CONTENT_TYPE, "text/plain").build());
		assertThat(result instanceof Message).isTrue();

		function = catalog.lookup("parseToListOfMessages", "application/json");
		assertThat(function).isNotNull();
		result = function.apply(MessageBuilder.withPayload("1, 2, 3".getBytes()).setHeader(MessageHeaders.CONTENT_TYPE, "text/plain").build());
		assertThat(result instanceof Message).isFalse();
	}

	/**
	 * The following two tests test the fallback mechanism when an accept header has several values.
	 * The function produces Integer, which cannot be serialized by the default converter supporting text/plain
	 * (StringMessageConverter) but can by the one supporting application/json, which comes second.
	 */
	//@Test
	public void testMultipleOrderedAcceptValues() throws Exception {
		FunctionCatalog catalog = this.configureCatalog(MultipleOrderedAcceptValuesConfiguration.class);
		Function<String, Message<byte[]>> function = catalog.lookup("beanFactoryAwareFunctionRegistryTests.MultipleOrderedAcceptValuesConfiguration", "text/plain,application/json");
		assertThat(function).isNotNull();
		Message<byte[]> result = function.apply("hello");
		assertThat(result.getPayload()).isEqualTo("5".getBytes("UTF-8"));
	}

	@Test
	public void testMultipleOrderedAcceptValuesMessageOutput() throws Exception {
		FunctionCatalog catalog = this.configureCatalog(MultipleOrderedAcceptValuesAsMessageOutputConfiguration.class);
		Function<String, Message<byte[]>> function = catalog.lookup(
				"beanFactoryAwareFunctionRegistryTests.MultipleOrderedAcceptValuesAsMessageOutputConfiguration",
				"text/plain,application/json");
		assertThat(function).isNotNull();
		Message<byte[]> result = function.apply("hello");
		assertThat(result.getPayload()).isEqualTo("5".getBytes("UTF-8"));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSerializationWithCompatibleWildcardSubtypeAcceptHeader() {
		FunctionCatalog catalog = this.configureCatalog(NegotiatingMessageConverterConfiguration.class);
		FunctionInvocationWrapper function = catalog.lookup("echo", "text/*");

		Message<Tuple2<String, String>> tupleResult = (Message<Tuple2<String, String>>) function.apply(MessageBuilder
				.withPayload(Tuples.of("bonjour", "monde"))
				.setHeader(MessageHeaders.CONTENT_TYPE, MimeType.valueOf("text/csv"))
				.build()
		);

		assertThat(tupleResult.getHeaders().get(MessageHeaders.CONTENT_TYPE)).isEqualTo(MimeType.valueOf("text/csv"));
		assertThat(tupleResult.getHeaders().get("accept")).isNull();

		Message<Date> dateResult = (Message<Date>) function.apply(MessageBuilder
				.withPayload(123)
				.setHeader(MessageHeaders.CONTENT_TYPE, MimeType.valueOf("text/integer"))
				.build()
		);

		assertThat(dateResult.getHeaders().get(MessageHeaders.CONTENT_TYPE)).isEqualTo(MimeType.valueOf("text/integer"));
		assertThat(dateResult.getHeaders().get("accept")).isNull();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testWithComplexHierarchyAndTypeConversion() {
		FunctionCatalog catalog = this.configureCatalog(ReactiveFunctionImpl.class);
		Function<Object, Flux> f = catalog.lookup("");
		assertThat(f.apply(new GenericMessage("23")).blockFirst()).isEqualTo(23);
		assertThat(f.apply(Flux.just("25")).blockFirst()).isEqualTo(25);
		assertThat(f.apply(Flux.just(25)).blockFirst()).isEqualTo(25);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testRegisteringWithTypeThatDoesNotMatchDiscoveredType() {
		FunctionCatalog catalog = this.configureCatalog(EmptyConfiguration.class);
		Function func = catalog.lookup("func");
		assertThat(func).isNull();
		FunctionRegistry registry = (FunctionRegistry) catalog;
		try {
			FunctionRegistration registration = new FunctionRegistration(new MyFunction(), "a").type(FunctionType.from(Integer.class).to(String.class));
			registry.register(registration);
			fail();
		}
		catch (IllegalStateException e) {
			// good as we expect it to fail
		}
		//
		try {
			FunctionRegistration registration = new FunctionRegistration(new MyFunction(), "b").type(FunctionType.from(String.class).to(Integer.class));
			registry.register(registration);
			fail();
		}
		catch (IllegalStateException e) {
			// good as we expect it to fail
		}
		//
		FunctionRegistration c = new FunctionRegistration(new MyFunction(), "c").type(FunctionType.from(String.class).to(String.class));
		registry.register(c);
		//
		FunctionRegistration d = new FunctionRegistration(new RawFunction(), "d").type(FunctionType.from(Person.class).to(String.class));
		registry.register(d);
		//
		FunctionRegistration e = new FunctionRegistration(new RawFunction(), "e").type(FunctionType.from(Object.class).to(Object.class));
		registry.register(e);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testValueWrappedInMessageIfNecessary() {
		FunctionCatalog catalog = this.configureCatalog(PojoToMessageFunctionCompositionConfiguration.class);
		Function f = catalog.lookup("uppercase|echo");
		assertThat(f.apply("hello")).isEqualTo("HELLO");
		f = catalog.lookup("toJson|uppercasePerson");
		assertThat(f.apply("Bubbles")).isEqualTo("BUBBLES");
	}

	@Test
	public void testSupplierConsumerAsRunnable() {
		FunctionCatalog catalog = this.configureCatalog(SampleFunctionConfiguration.class);
		Runnable f = catalog.lookup("numberword|imperativeConsumer");
		f.run();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testWrappedWithAroundAdviseConfiguration() {
		FunctionCatalog catalog = this.configureCatalog(WrappedWithAroundAdviseConfiguration.class);
		Function f = catalog.lookup("uppercase");
		Message result = (Message) f.apply(new GenericMessage<String>("hello"));
		assertThat(result.getHeaders().get("before")).isEqualTo("foo");
		assertThat(result.getHeaders().get("after")).isEqualTo("bar");
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testEachElementInFluxIsProcessed() {
		FunctionCatalog catalog = this.configureCatalog(SampleFunctionConfiguration.class);
		Function f = catalog.lookup("uppercasePerson");

		Flux flux = Flux.just("{\"id\":1, \"name\":\"oleg\"}", "{\"id\":2, \"name\":\"seva\"}");
		Flux result = (Flux) f.apply(flux);

		List<Person> list = (List) result.collectList().block();
		assertThat(list.size()).isEqualTo(2);
		assertThat(list.get(0).name).isEqualTo("OLEG");
		assertThat(list.get(1).name).isEqualTo("SEVA");



		result = (Flux) f.apply(new GenericMessage<String>("[{\"id\":1, \"name\":\"oleg\"}, {\"id\":2, \"name\":\"seva\"}]"));
		list = (List) result.collectList().block();
		assertThat(list.size()).isEqualTo(2);
		assertThat(list.get(0).name).isEqualTo("OLEG");
		assertThat(list.get(1).name).isEqualTo("SEVA");
	}

	@Test
	public void testGH_608() {
		ApplicationContext context = new SpringApplicationBuilder(SampleFunctionConfiguration.class)
				.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true");
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);

		Consumer<Flux<String>> consumer = catalog.lookup("reactivePojoConsumer");
		consumer.accept(Flux.just("{\"name\":\"Ricky\"}"));
		SampleFunctionConfiguration config = context.getBean(SampleFunctionConfiguration.class);
		assertThat(((Person) config.consumerInputRef.get()).getName()).isEqualTo("Ricky");
	}

	@Test
	public void testGH_611() {
		FunctionCatalog catalog = this.configureCatalog(NegotiatingMessageConverterConfiguration.class);
		Supplier<Message<Integer>> f = catalog.lookup("supplier", "text/*");
		assertThat(f.get().getHeaders().get(MessageHeaders.CONTENT_TYPE)).isEqualTo(MimeTypeUtils.parseMimeType("text/*"));
	}

	@Test
	public void testGH_608_C() {
		ApplicationContext context = new SpringApplicationBuilder(SampleFunctionConfiguration.class)
			.run("--logging.level.org.springframework.cloud.function=DEBUG",
				"--spring.main.lazy-initialization=true");
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);

		String productJson = "{\"key\":\"someKey\",\"data\": {\"name\":\"bike\"}}";

		FunctionInvocationWrapper function = catalog.lookup("echoGenericObjectFlux", "application/json");
		Message<byte[]> result = ((Flux<Message<byte[]>>) function.apply(productJson)).blockFirst();
		assertThat(new String(result.getPayload())).isEqualTo("\"bike\"");
	}

	@Test
	public void testGH_609() {
		FunctionCatalog catalog = this.configureCatalog(SampleFunctionConfiguration.class);
		Function<Publisher<String>, Publisher<String>> f = catalog.lookup("monoToMono");
		Mono<String> result = (Mono<String>) f.apply(Mono.just("hello"));
		assertThat(result.block()).isEqualTo("hello");

		result = (Mono<String>) f.apply(Flux.just("hello"));
		assertThat(result.block()).isEqualTo("hello");
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testGH_635() throws Exception {
		FunctionCatalog catalog = this.configureCatalog(SCF_GH_635ConfigurationAsFunction.class);
		Function lmFunction = catalog.lookup("emptyMessageList", "application/json");
		List<Message<?>> emptyListOfMessages = (List<Message<?>>) lmFunction.apply(MessageBuilder.withPayload("hello").build());
		assertThat(emptyListOfMessages).isEmpty();
		emptyListOfMessages = (List<Message<?>>) lmFunction.apply("hello");
		assertThat(emptyListOfMessages).isEmpty();

		JsonMapper mapper = this.context.getBean(JsonMapper.class);
		Function lsFunction = catalog.lookup("emptyStringList", "application/json");
		Message<byte[]> emptyListOfString = (Message<byte[]>) lsFunction.apply(MessageBuilder.withPayload("hello").build());
		List resultList = mapper.fromJson(emptyListOfString.getPayload(), List.class);
		assertThat(resultList).isEmpty();
		emptyListOfString = (Message<byte[]>) lsFunction.apply("hello");
		resultList = mapper.fromJson(emptyListOfString.getPayload(), List.class);
		assertThat(resultList).isEmpty();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testGH_768() throws Exception {
		FunctionCatalog catalog = this.configureCatalog(SCF_GH_768ConfigurationAsFunction.class);
		Function function = catalog.lookup("echo");

		JsonMapper mapper = this.context.getBean(JsonMapper.class);
		String date = mapper.toString(new Date());
		String result = (String) function.apply("{\"date\":" + date + "}");
		assertThat(result).startsWith("{date=");
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testArrayPayloadOnFluxFunction() throws Exception {
		FunctionCatalog catalog = this.configureCatalog(SampleFunctionConfiguration.class);
		FunctionInvocationWrapper lmFunction = catalog.lookup("uppercaseFlux", "application/json");
		lmFunction.setSkipOutputConversion(true);
		List<String> list = new ArrayList<>();
		list.add("Ricky");
		list.add("Julien");
		list.add("Bubbles");
		Publisher p = (Publisher) lmFunction.apply(MessageBuilder.withPayload(list).setHeader(MessageHeaders.CONTENT_TYPE, "application/json").build());
		List<Object> result = new ArrayList<>();
		for (Object value : Flux.from(p).toIterable()) {
			result.add(value);
		}
		assertThat(result.size()).isEqualTo(3);
	}

	@Test
	// see GH-707
	public void testConcurrencyOnLookup() throws Exception {
		AtomicInteger counter = new AtomicInteger();

		ExecutorService executor = Executors.newFixedThreadPool(10);
		for (int i = 0; i < 10; i++) {
			FunctionCatalog catalog = this.configureCatalog(SampleFunctionConfiguration.class);
			for (int y = 0; y < 10; y++) {
				executor.execute(() -> {
					assertThat((FunctionInvocationWrapper) catalog.lookup("uppercase|reverse", "application/json")).isNotNull();
					counter.incrementAndGet();
				});
			}
		}

		executor.shutdown();
		executor.awaitTermination(10000, TimeUnit.MILLISECONDS);
		assertThat(counter.get()).isEqualTo(100);
	}

	@EnableAutoConfiguration
	public static class PojoToMessageFunctionCompositionConfiguration {

		@Bean
		public Function<String, String> uppercase() {
			return v -> v.toUpperCase();
		}

		@Bean
		public Function<Message<String>, String> echo() {
			return v -> v.getPayload();
		}

		@Bean
		public Function<String, String> toJson() {
			return v -> "{\"id\":1, \"name\":\"" + v + "\"}";
		}

		@Bean
		public Function<Message<Person>, String> uppercasePerson() {
			return v -> v.getPayload().getName().toUpperCase();
		}
	}

	@EnableAutoConfiguration
	public static class EmptyConfiguration {

	}

	public interface ReactiveFunction<S, T> extends Function<Flux<S>, Flux<T>> {

	}

	@Component
	@EnableAutoConfiguration
	public static class ReactiveFunctionImpl implements ReactiveFunction<String, Integer> {
		@Override
		public Flux<Integer> apply(Flux<String> inFlux) {
			return inFlux.map(v -> Integer.parseInt(v));
		}
	}

	@EnableAutoConfiguration
	public static class CollectionOutConfiguration {

		@Bean
		public Function<String, List<String>> parseToList() {
			return v -> Arrays.asList(v.split(","));
		}

		@Bean
		public Function<String, List<Message<String>>> parseToListOfMessages() {
			return v -> {
				List<Message<String>> list = Arrays.asList(v.split(",")).stream()
						.map(value -> MessageBuilder.withPayload(value).build()).collect(Collectors.toList());
				return list;
			};
		}
	}

	@EnableAutoConfiguration
	public static class NegotiatingMessageConverterConfiguration {

		@Bean
		public Supplier<Integer> supplier() {
			return () -> 123;
		}

		@Bean
		public Function<String, String> echo() {
			return v -> v;
		}

		@Bean
		public MessageConverter messageConverterA() {
			return new ConverterA();
		}

		@Bean
		public MessageConverter messageConverterB() {
			return new ConverterB();
		}

		@Bean
		public MessageConverter messageConverterC() {
			return new ConverterC();
		}

		public static class ConverterC extends ConverterA {
			ConverterC() {
				super("text/*");
			}

			@Override
			protected Object convertFromInternal(
					Message<?> message, Class<?> targetClass, @Nullable Object conversionHint) {
				return message.getPayload().toString();
			}

			@Override
			public Object convertToInternal(Object rawPayload, MessageHeaders headers, Object conversionHint) {
				return rawPayload;
			}

			@Override
			protected boolean canConvertFrom(Message<?> message, @Nullable Class<?> targetClass) {
				return supportsMimeType(message.getHeaders()) && Integer.class.isAssignableFrom(targetClass)
						&& message.getPayload() instanceof Integer;
			}

			@Override
			protected boolean canConvertTo(Object payload, @Nullable MessageHeaders headers) {
				return payload instanceof Integer;
			}
		}

		public static class ConverterB extends ConverterA {
			ConverterB() {
				super("text/integer");
			}

			@Override
			protected Object convertFromInternal(
					Message<?> message, Class<?> targetClass, @Nullable Object conversionHint) {
				return message.getPayload().toString();
			}

			@Override
			public Object convertToInternal(Object rawPayload, MessageHeaders headers, Object conversionHint) {
				return Integer.parseInt((String) rawPayload);
			}

			@Override
			protected boolean canConvertFrom(Message<?> message, @Nullable Class<?> targetClass) {
				return supportsMimeType(message.getHeaders()) && String.class.isAssignableFrom(targetClass)
						&& message.getPayload() instanceof Integer;
			}

			@Override
			protected boolean canConvertTo(Object payload, @Nullable MessageHeaders headers) {
				return payload instanceof String;
			}
		}

		private static class ConverterA extends AbstractMessageConverter {

			ConverterA() {
				this("text/csv");
			}

			ConverterA(String mimeType) {
				super(singletonList(MimeType.valueOf(mimeType)));
			}

			@SuppressWarnings("unchecked")
			@Override
			protected Object convertFromInternal(
					Message<?> message, Class<?> targetClass, @Nullable Object conversionHint) {
				Tuple2<String, String> payload = (Tuple2<String, String>) message.getPayload();

				String convertedPayload = payload.getT1() + "," + payload.getT2();
				return convertedPayload;
			}

			@Override
			public Object convertToInternal(Object rawPayload, MessageHeaders headers, Object conversionHint) {
				return Tuples.fromArray(((String) rawPayload).split(","));
			}

			@Override
			protected boolean canConvertFrom(Message<?> message, @Nullable Class<?> targetClass) {
				return supportsMimeType(message.getHeaders()) && String.class.isAssignableFrom(targetClass)
						&& message.getPayload() instanceof Tuple2;
			}

			@Override
			protected boolean canConvertTo(Object payload, @Nullable MessageHeaders headers) {
				return payload instanceof String && ((String) payload).split(",").length == 2;
			}

			@Override
			protected boolean supports(Class<?> clazz) {
				throw new UnsupportedOperationException();
			}
		}
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class WrappedWithAroundAdviseConfiguration {
		@Bean
		public Function<Message<String>, Message<String>> uppercase() {
			return v -> MessageBuilder.withPayload(v.getPayload().toUpperCase()).copyHeaders(v.getHeaders()).build();
		}

		@Bean
		public FunctionAroundWrapper wrapper() {
			return new FunctionAroundWrapper() {

				@SuppressWarnings("unchecked")
				@Override
				protected Object doApply(Object input, FunctionInvocationWrapper targetFunction) {
					// in this test we know input is a Message
					Message<?> mInput = (Message<?>) input;
					MessageBuilder.fromMessage(mInput).setHeader("before", "foo").build();
					Message<Object> result = (Message<Object>) targetFunction.apply(MessageBuilder.fromMessage(mInput).setHeader("before", "foo").build());
					return MessageBuilder.fromMessage(result).setHeader("after", "bar").build();
				}
			};
		}
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class SampleFunctionConfiguration {

		AtomicReference<Object> consumerInputRef = new AtomicReference<>();

		@Bean
		public Function<Flux<Message<Event<String, Person>>>, Flux<String>> echoGenericObjectFlux() {
			return x -> x.map(eventMessage -> eventMessage.getPayload().getData().getName());
		}

		@Bean
		public Function<Person, Person> uppercasePerson() {
			return person -> {
				return new Person(person.getName().toUpperCase(), person.getId());
			};
		}

		@Bean
		public Supplier<String> numberword() {
			return () -> "one";
		}

		@Bean
		public Function<Map<String, Object>, Person> maptopojo() {
			return map -> {
				Person person = new Person((String) map.get("name"), Integer.parseInt((String) map.get("id")));
				return person;
			};
		}

		@Bean
		public Function<String, String> uppercase() {
			return v -> v.toUpperCase();
		}

		@Bean
		public Function<String, Void> consumerFunction() {
			return v -> {
				System.out.println("Value: " + v);
				return null;
			};
		}

		@Bean
		public Function<Flux<String>, Flux<String>> uppercaseFlux() {
			return flux -> flux.map(v -> v.toUpperCase());
		}

		@Bean
		public Function<Void, String> voidInputFunction() {
			return v -> "voidInputFunction";
		}

		@Bean
		public Function<Flux<Void>, Flux<String>> voidInputFunctionReactive() {
			return flux -> Flux.just("voidInputFunctionReactive");
		}

		@Bean
		public Function<Mono<Void>, Flux<String>> voidInputFunctionReactive2() {
			return mono -> Flux.just("voidInputFunctionReactive2");
		}

		@Bean
		public Function<String, String> reverse() {
			return value -> new StringBuilder(value).reverse().toString();
		}

		@Bean
		public Function<Flux<String>, Flux<String>> reverseFlux() {
			return flux -> flux.map(value -> {
				return new StringBuilder(value).reverse().toString();
			});
		}


		@Bean
		public Function<Mono<Void>, Mono<Void>> monoVoidToMonoVoid() {
			return mono -> mono.doOnSuccess(v -> System.out.println("HELLO"));
		}

		// ============= MESSAGE-IN and MESSAGE-OUT functions ============

		// ============= MULTI-INPUT and MULTI-OUTPUT functions ============

		@Bean
		public Function<Tuple2<Flux<String>, Flux<Integer>>, Flux<String>> multiInputSingleOutputViaReactiveTuple() {
			return tuple -> {
				Flux<String> stringStream = tuple.getT1();
				Flux<Integer> intStream = tuple.getT2();
				return Flux.zip(stringStream, intStream, (string, integer) -> string + "-" + integer);
			};
		}
		//========

		// MULTI-OUTPUT
		@Bean
		public Function<Flux<Person>, Tuple3<Flux<Person>, Flux<String>, Flux<Integer>>> multiOutputAsTuple() {
			return flux -> {
				Flux<Person> pubSubFlux = flux.publish().autoConnect(3);
				Flux<String> nameFlux = pubSubFlux.map(person -> person.getName());
				Flux<Integer> idFlux = pubSubFlux.map(person -> person.getId());
				return Tuples.of(pubSubFlux, nameFlux, idFlux);
			};
		}

		public Function<Flux<Person>, Flux<Tuple3<Person, String, Integer>>> multiOutputAsTuple2() {
			return null;
		}
		//========

		@Bean
		public Function<Mono<String>, Mono<Void>> monoToMonoVoid() {
			return null;
		}

		@Bean
		public Function<Mono<String>, Mono<String>> monoToMono() {
			return mono -> mono;
		}

		@Bean
		public Function<Flux<Void>, Flux<Void>> fluxVoidToFluxVoid() {
			return null;
		}

		@Bean
		public Function<Mono<String>, Flux<Void>> monoToFluxVoid() {
			return null;
		}

		@Bean
		public Function<Flux<String>, Mono<Void>> fluxToMonoVoid() {
			return null;
		}

		@Bean
		public Function<Mono<String>, Flux<String>> monoToFlux() {
			return null;
		}

		@Bean
		public Function<Flux<String>, Mono<String>> fluxToMono() {
			return null;
		}

		@Bean
		public Supplier<String> imperativeSupplier() {
			return null;
		}

		@Bean
		public Supplier<Flux<String>> reactiveSupplier() {
			return null;
		}

		@Bean
		public Consumer<String> imperativeConsumer() {
			return System.out::println;
		}

		@Bean
		// Perhaps it should not be allowed. Recommend Function<Flux, Mono<Void>>
		public Consumer<Flux<String>> reactiveConsumer() {
			return null;
		}

		@Bean
		// Perhaps it should not be allowed. Recommend Function<Flux, Mono<Void>>
		public Consumer<Flux<Person>> reactivePojoConsumer() {
			return flux -> flux.subscribe(v -> consumerInputRef.set(v));
		}
	}

	@EnableAutoConfiguration
	public static class SCF_GH_409ConfigurationAsSupplier {

		@Bean
		public Serializable blah() {
			return new Foo();
		}

		private static class Foo implements Supplier<Object>, Serializable {

			@Override
			public Object get() {
				// TODO Auto-generated method stub
				return null;
			}

		}
	}

	@EnableAutoConfiguration
	public static class SCF_GH_409ConfigurationAsFunction {

		@Bean
		public Serializable blah() {
			return new Foo();
		}

		private static class Foo implements Function<Object, Object>, Serializable {

			@Override
			public Object apply(Object t) {
				// TODO Auto-generated method stub
				return null;
			}
		}
	}

	@EnableAutoConfiguration
	public static class SCF_GH_635ConfigurationAsFunction {

		@Bean
		public Function<String, List<Message<?>>> emptyMessageList() {
			return input -> Collections.emptyList();
		}

		@Bean
		public Function<String, List<String>> emptyStringList() {
			return input -> Collections.emptyList();
		}
	}

	@EnableAutoConfiguration
	public static class SCF_GH_768ConfigurationAsFunction {
		@Bean
		public Function<Map<String, Date>, String> echoToString() {
			return data -> {
				for (Entry<String, Date> dataEntry : data.entrySet()) {
					assertThat(dataEntry.getValue()).isInstanceOf(Date.class); // would fail if value would not be converted to Person
				}
				return data.toString();
			};
		}
	}

	public static class Person {
		private String name;
		private int id;
		public Person() {

		}
		public Person(String name, int id) {
			this.name = name;
			this.id = id;
		}
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public int getId() {
			return id;
		}
		public void setId(int id) {
			this.id = id;
		}
		@Override
		public String toString() {
			return "Person: " + name + "/" + id;
		}
	}

	@EnableAutoConfiguration
	@Configuration
	@Component
	public static class MyFunction implements Function<String, String> {

		@Override
		public String apply(String t) {
			return t;
		}

	}

	public static class RawFunction implements Function<Object, Object> {

		@Override
		public Object apply(Object t) {
			return t;
		}

	}

	@EnableAutoConfiguration
	@Configuration
	@Component
	public static class ByteArrayFunction implements Function<byte[], byte[]> {

		@Override
		public byte[] apply(byte[] bytes) {
			byte[] result = new byte[bytes.length];
			for (int i = 0; i < bytes.length; i++) {
				result[i] = bytes[bytes.length - i - 1];
			}
			return result;
		}
	}

	@EnableAutoConfiguration
	@Configuration
	@Component
	public static class MultipleOrderedAcceptValuesConfiguration implements Function<String, Integer> {

		@Override
		public Integer apply(String t) {
			return t.length();
		}
	}

	@EnableAutoConfiguration
	@Configuration
	@Component
	public static class MultipleOrderedAcceptValuesAsMessageOutputConfiguration implements Function<String, Message<Integer>> {

		@Override
		public Message<Integer> apply(String t) {
			return MessageBuilder.withPayload(t.length()).build();
		}

	}

	@EnableAutoConfiguration
	@Configuration
	public static class ComplexTypeFunctionConfiguration {
		@Bean
		public Function<Event<String, Product>, String> function() {
			return v -> v.getData().getName().toUpperCase();
		}
	}

	private static class Product {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	private static class Event<K, V> {

		private K key;

		public K getKey() {
			return key;
		}

		public void setKey(K key) {
			this.key = key;
		}

		private V data;

		public V getData() {
			return data;
		}

		public void setData(V data) {
			this.data = data;
		}
	}
}
