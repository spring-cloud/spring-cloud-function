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


import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Ignore;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class BeanFactoryAwareFunctionRegistryTests {

	private FunctionCatalog configureCatalog() {
		ApplicationContext context = new SpringApplicationBuilder(SampleFunctionConfiguration.class)
				.run("--logging.level.org.springframework.cloud.function=DEBUG");
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		return catalog;
	}

	@Test
	public void testImperativeFunction() {
		FunctionCatalog catalog = this.configureCatalog();

		Function<String, String> asIs = catalog.lookup("uppercase");
		assertThat(asIs.apply("uppercase")).isEqualTo("UPPERCASE");

		Function<Flux<String>, Flux<String>> asFlux = catalog.lookup("uppercase");
		List<String> result = asFlux.apply(Flux.just("uppercaseFlux", "uppercaseFlux2")).collectList().block();
		assertThat(result.get(0)).isEqualTo("UPPERCASEFLUX");
		assertThat(result.get(1)).isEqualTo("UPPERCASEFLUX2");

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
	@Ignore
	public void testImperativeVoidInputFunction() {
		FunctionCatalog catalog = this.configureCatalog();

		Function<String, String> anyInputSignature = catalog.lookup("voidInputFunction");
		assertThat(anyInputSignature.apply("uppercase")).isEqualTo("voidInputFunction");
		assertThat(anyInputSignature.apply("blah")).isEqualTo("voidInputFunction");
		assertThat(anyInputSignature.apply(null)).isEqualTo("voidInputFunction");

		Function<Void, String> asVoid = catalog.lookup("voidInputFunction");
		assertThat(asVoid.apply(null)).isEqualTo("voidInputFunction");

		Function<Mono<Void>, Mono<String>> asMonoVoidFlux = catalog.lookup("voidInputFunction");
		String result = asMonoVoidFlux.apply(Mono.empty()).block();
		assertThat(result).isEqualTo("voidInputFunction");

		Function<Flux<Void>, Flux<String>> asFluxVoidFlux = catalog.lookup("voidInputFunction");
		List<String> resultList = asFluxVoidFlux.apply(Flux.empty()).collectList().block();
		assertThat(resultList.get(0)).isEqualTo("voidInputFunction");
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
	@Test(expected = ClassCastException.class)
	public void testReactiveFunctionWithImperativeInputAndOutputFail() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<String, String> reverse = catalog.lookup("reverseFlux");
		String result = reverse.apply("reverseFlux");
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


	@Test
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


	@EnableAutoConfiguration
	@Configuration
	protected static class SampleFunctionConfiguration {

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
			return null;
		}

		@Bean
		// Perhaps it should not be allowed. Recommend Function<Flux, Mono<Void>>
		public Consumer<Flux<String>> reactiveConsumer() {
			return null;
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
		public String toString() {
			return "Person: " + name + "/" + id;
		}
	}
}
