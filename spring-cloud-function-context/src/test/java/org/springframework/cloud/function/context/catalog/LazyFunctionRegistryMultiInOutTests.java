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

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.Ignore;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class LazyFunctionRegistryMultiInOutTests {

	private FunctionCatalog configureCatalog() {
		ApplicationContext context = new SpringApplicationBuilder(SampleFunctionConfiguration.class)
				.run("--logging.level.org.springframework.cloud.function=DEBUG");
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		return catalog;
	}

	/*
	 * This test validates <Tuple2<Flux<String>, Flux<Integer>> without any type conversion
	 */
	@Test
	public void testMultiInput() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<Tuple2<Flux<String>, Flux<Integer>>, Flux<String>> multiInputFunction =
									catalog.lookup("multiInputSingleOutputViaReactiveTuple");
		Flux<String> stringStream = Flux.just("one", "two", "three");
		Flux<Integer> intStream = Flux.just(1, 2, 3);

		List<String> result = multiInputFunction.apply(Tuples.of(stringStream, intStream)).collectList().block();
		System.out.println(result);
	}

	@SuppressWarnings("unused")
	@Test
	@Ignore
	public void testMultiInputBiFunction() {
		FunctionCatalog catalog = this.configureCatalog();
		BiFunction<Flux<String>, Flux<Integer>, Flux<String>> multiInputFunction =
									catalog.lookup(BiFunction.class, "multiInputSingleOutputViaBiFunction");
		Flux<String> stringStream = Flux.just("one", "two", "three");
		Flux<Integer> intStream = Flux.just(1, 2, 3);

//		List<String> result = multiInputFunction.apply(Tuples.of(stringStream, intStream)).collectList().block();
//		System.out.println(result);
	}

	/*
	 * This test invokes the same function as above but with types reversed.
	 * While the target function remains <Tuple2<Flux<String>, Flux<Integer>>
	 * it is actually invoked as Tuple2<Flux<Integer>, Flux<String>>
	 * hence showcasing type conversion using Spring's ConversionService
	 */
	@Test
	public void testMultiInputWithConversion() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<Tuple2<Flux<Integer>, Flux<String>>, Flux<String>> multiInputFunction =
									catalog.lookup("multiInputSingleOutputViaReactiveTuple");
		Flux<Integer> stringStream = Flux.just(11, 22, 33);
		Flux<String> intStream = Flux.just("1","2", "2");

		List<String> result = multiInputFunction.apply(Tuples.of(stringStream, intStream)).collectList().block();
		System.out.println(result);
	}

	/*
	 * Same as above but with composing 'uppercase' function essentially validating \
	 * composition in multi-input scenario
	 */
	@Test
	public void testMultiInputWithComposition() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<Tuple2<Flux<String>, Flux<String>>, Flux<String>> multiInputFunction =
									catalog.lookup("multiInputSingleOutputViaReactiveTuple|uppercase");
		Flux<String> stringStream = Flux.just("one", "two", "three");
		Flux<String> intStream = Flux.just("1", "2", "3");

		List<String> result = multiInputFunction.apply(Tuples.of(stringStream, intStream)).collectList().block();
		System.out.println(result);
	}

	/*
	 * This is basically the repeater function currently prototyped in Riff
	 * The only difference it uses Tuple2 instead of BiFunction (which we will support anyway)
	 */
	@Test
	public void testMultiOutputAsArray() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<Tuple2<Flux<String>, Flux<Integer>>, Flux<?>[]> repeater =
									catalog.lookup("repeater");
		Flux<String> stringStream = Flux.just("one", "two", "three");
		Flux<Integer> intStream = Flux.just(3, 2, 1);

		Flux<?>[] result = repeater.apply(Tuples.of(stringStream, intStream));
		result[0].subscribe(System.out::println);
		result[1].subscribe(System.out::println);
	}


	/*
	 * This test demonstrates single input into multiple outputs
	 * as Tuple3 thus making output types known.
	 *
	 * The input is a POJO (Person)
	 * no conversion
	 */
	@Test
	public void testMultiOutputAsTuplePojoInInputTypeMatch() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<Flux<Person>, Tuple3<Flux<Person>, Flux<String>, Flux<Integer>>> multiOutputFunction =
									catalog.lookup("multiOutputAsTuplePojoIn");
		Flux<Person> personStream = Flux.just(new Person("Uncle Sam", 1), new Person("Oncle Pierre", 2));

		Tuple3<Flux<Person>, Flux<String>, Flux<Integer>> result = multiOutputFunction.apply(personStream);
		result.getT1().subscribe(v -> System.out.println("=> 1: " + v));
		result.getT2().subscribe(v -> System.out.println("=> 2: " + v));
		result.getT3().subscribe(v -> System.out.println("=> 3: " + v));
	}

	/*
	 * This test is identical to the previous one with the exception that the
	 * input is a Message with payload as JSON byte array representation of Person (expected by the target function),
	 * thus demonstrating Message Conversion
	 */
	@Test
	public void testMultiOutputAsTuplePojoInInputByteArray() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<Flux<Message<byte[]>>, Tuple3<Flux<Person>, Flux<String>, Flux<Integer>>> multiOutputFunction =
									catalog.lookup("multiOutputAsTuplePojoIn");

		Message<byte[]> uncleSam = MessageBuilder.withPayload("{\"name\":\"Uncle Sam\",\"id\":1}".getBytes(StandardCharsets.UTF_8))
				.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
				.build();
		Message<byte[]> unclePierre = MessageBuilder.withPayload("{\"name\":\"Oncle Pierre\",\"id\":2}".getBytes(StandardCharsets.UTF_8))
				.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
				.build();
		Flux<Message<byte[]>> personStream = Flux.just(uncleSam, unclePierre);

		Tuple3<Flux<Person>, Flux<String>, Flux<Integer>> result = multiOutputFunction.apply(personStream);
		result.getT1().subscribe(v -> System.out.println("=> 1: " + v));
		result.getT2().subscribe(v -> System.out.println("=> 2: " + v));
		result.getT3().subscribe(v -> System.out.println("=> 3: " + v));
	}

	/*
	 * This is another variation of the above. In this case the signature of the target function is
	 * <Flux<Message<Person>>, Tuple3<Flux<Person>, Flux<String>, Flux<Integer>>> yet we are sending
	 * Message with payload as byte[] which is converted to Person and then embedded in new Message<Person>
	 * passed to a function
	 */
	@Test
	public void testMultiOutputAsTuplePojoInInputByteArrayInputTypePojoMessage() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<Flux<Message<byte[]>>, Tuple3<Flux<Person>, Flux<String>, Flux<Integer>>> multiOutputFunction =
									catalog.lookup("multiOutputAsTupleMessageIn");

		Message<byte[]> uncleSam = MessageBuilder.withPayload("{\"name\":\"Uncle Sam\",\"id\":1}".getBytes(StandardCharsets.UTF_8))
				.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
				.build();
		Message<byte[]> unclePierre = MessageBuilder.withPayload("{\"name\":\"Oncle Pierre\",\"id\":2}".getBytes(StandardCharsets.UTF_8))
				.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
				.build();
		Flux<Message<byte[]>> personStream = Flux.just(uncleSam, unclePierre);

		Tuple3<Flux<Person>, Flux<String>, Flux<Integer>> result = multiOutputFunction.apply(personStream);
		result.getT1().subscribe(v -> System.out.println("=> 1: " + v));
		result.getT2().subscribe(v -> System.out.println("=> 2: " + v));
		result.getT3().subscribe(v -> System.out.println("=> 3: " + v));
	}

	@Test
	public void testMultiToMulti() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<Tuple3<Flux<String>, Flux<String>, Flux<Integer>>, Tuple2<Flux<Person>, Mono<Long>>> multiTuMulti =
									catalog.lookup("multiTuMulti");

		Flux<String> firstFlux = Flux.just("Unlce", "Oncle");
		Flux<String> secondFlux = Flux.just("Sam", "Pierre");
		Flux<Integer> thirdFlux = Flux.just(1, 2);

		Tuple2<Flux<Person>, Mono<Long>> result = multiTuMulti.apply(Tuples.of(firstFlux, secondFlux, thirdFlux));
		result.getT1().subscribe(v -> System.out.println("=> 1: " + v));
		result.getT2().subscribe(v -> System.out.println("=> 2: " + v));
	}

	@Test
	public void testMultiToMultiWithMessageByteArrayPayload() {
		FunctionCatalog catalog = this.configureCatalog();
		Function<Tuple3<Flux<Message<byte[]>>, Flux<Message<byte[]>>, Flux<Message<byte[]>>>, Tuple2<Flux<Message<byte[]>>, Mono<Message<byte[]>>>> multiTuMulti =
									catalog.lookup("multiTuMulti", MimeTypeUtils.parseMimeType("application/json"), MimeTypeUtils.parseMimeType("application/json"));

		Flux<Message<byte[]>> firstFlux = Flux.just(
				MessageBuilder.withPayload("Unlce".getBytes()).setHeader(MessageHeaders.CONTENT_TYPE, "text/plain").build(),
				MessageBuilder.withPayload("Onlce".getBytes()).setHeader(MessageHeaders.CONTENT_TYPE, "text/plain").build());
		Flux<Message<byte[]>> secondFlux = Flux.just(
				MessageBuilder.withPayload("Sam".getBytes()).setHeader(MessageHeaders.CONTENT_TYPE, "text/plain").build(),
				MessageBuilder.withPayload("Pierre".getBytes()).setHeader(MessageHeaders.CONTENT_TYPE, "text/plain").build());

		ByteBuffer one = ByteBuffer.allocate(4);
		one.putInt(1);
		ByteBuffer two = ByteBuffer.allocate(4);
		two.putInt(2);

		Flux<Message<byte[]>> thirdFlux = Flux.just(
				MessageBuilder.withPayload(one.array()).setHeader(MessageHeaders.CONTENT_TYPE, "octet-stream/integer").build(),
				MessageBuilder.withPayload(two.array()).setHeader(MessageHeaders.CONTENT_TYPE, "octet-stream/integer").build());

		Tuple2<Flux<Message<byte[]>>, Mono<Message<byte[]>>> result = multiTuMulti.apply(Tuples.of(firstFlux, secondFlux, thirdFlux));
		result.getT1().subscribe(v -> System.out.println("=> 1: " + v));
		result.getT2().subscribe(v -> System.out.println("=> 2: " + v));

		//Tuple2<Object, Object> d = multiTuMulti.apply(Tuples.of(firstFlux, secondFlux, thirdFlux));
	}


	@EnableAutoConfiguration
	@Configuration
	protected static class SampleFunctionConfiguration {

		@Bean
		public Function<String, String> uppercase() {
			return v -> v.toUpperCase();
		}

		// ============= MULTI-INPUT and MULTI-OUTPUT functions ============

		@Bean
		public Function<Tuple2<Flux<String>, Flux<Integer>>, Flux<String>> multiInputSingleOutputViaReactiveTuple() {
			return tuple -> {
				Flux<String> stringStream = tuple.getT1();
				Flux<Integer> intStream = tuple.getT2();
				return Flux.zip(stringStream, intStream, (string, integer) -> string + "-" + integer);
			};
		}

		@Bean
		public BiFunction<Flux<String>, Flux<Integer>, Flux<String>> multiInputSingleOutputViaBiFunction() {
			return (in1, in2) -> {
				Flux<String> stringStream = in1;
				Flux<Integer> intStream = in2;
				return Flux.zip(stringStream, intStream, (string, integer) -> string + "-" + integer);
			};
		}

		@Bean
		public Function<Flux<Person>, Tuple3<Flux<Person>, Flux<String>, Flux<Integer>>> multiOutputAsTuplePojoIn(){
			return flux -> {
				Flux<Person> pubSubFlux = flux.publish().autoConnect(3);
				Flux<String> nameFlux = pubSubFlux.map(person -> person.getName());
				Flux<Integer> idFlux = pubSubFlux.map(person -> person.getId());
				return Tuples.of(pubSubFlux, nameFlux, idFlux);
			};
		}

		@Bean
		public Function<Flux<Message<Person>>, Tuple3<Flux<Person>, Flux<String>, Flux<Integer>>> multiOutputAsTupleMessageIn(){
			return flux -> {
				Flux<Person> pubSubFlux = flux.map(message -> message.getPayload()).publish().autoConnect(3);
				Flux<String> nameFlux = pubSubFlux.map(person -> person.getName());
				Flux<Integer> idFlux = pubSubFlux.map(person -> person.getId());
				return Tuples.of(pubSubFlux, nameFlux, idFlux);
			};
		}

		@Bean
		public Function<Tuple3<Flux<String>, Flux<String>, Flux<Integer>>, Tuple2<Flux<Person>, Mono<Long>>> multiTuMulti(){
			return tuple -> {
				Flux<String> toStringFlux = tuple.getT1();
				Flux<String> nameFlux = tuple.getT2();
				Flux<Integer> idFlux = tuple.getT3();
				Flux<Person> person = toStringFlux.zipWith(nameFlux)
						.map(t -> t.getT1() + " " + t.getT2())
						.zipWith(idFlux)
						.map(t -> new Person(t.getT1(), t.getT2()));
				return Tuples.of(person, person.count());
			};
		}

		@Bean
		public MessageConverter byteArrayToIntegerMessageConverter()  {
			return new AbstractMessageConverter(MimeTypeUtils.parseMimeType("octet-stream/integer")) {

				@Override
				protected boolean supports(Class<?> clazz) {
					return Integer.class.isAssignableFrom(clazz);
				}

				protected Object convertFromInternal(
						Message<?> message, Class<?> targetClass, @Nullable Object conversionHint) {
					ByteBuffer wrappedPayload = ByteBuffer.wrap((byte[])message.getPayload());
					return wrappedPayload.getInt();
				}

				protected Object convertToInternal(
						Object payload, @Nullable MessageHeaders headers, @Nullable Object conversionHint) {

					return null;
				}
			};
		}

		@Bean
		public Function<Tuple2<Flux<String>, Flux<Integer>>, Flux<?>[]> repeater() {

			return tuple -> {
				Flux<String> stringFlux = tuple.getT1();
				Flux<Integer> integerFlux = tuple.getT2();

				Flux<Integer> sharedIntFlux = integerFlux.publish().autoConnect(2);

				Flux<String> repeated = stringFlux
						.zipWith(sharedIntFlux)
						.flatMap(t -> Flux.fromIterable(Collections.nCopies(t.getT2(), t.getT1())));

				Flux<Integer> sum = sharedIntFlux
						.buffer(3, 1)
						.map(l -> l.stream().mapToInt(Integer::intValue).sum());

				return new Flux[] { repeated, sum };
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
		public String toString() {
			return "Person: " + name + "/" + id;
		}
	}
}
