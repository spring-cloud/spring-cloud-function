/*
 * Copyright 2012-2019 the original author or authors.
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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.HybridFunctionalRegistrationTests.UppercaseFunction;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.config.JsonMessageConverter;
import org.springframework.cloud.function.json.GsonMapper;
import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Oleg Zhurakousky
 *
 */
public class SimpleFunctionRegistryTests {

	private CompositeMessageConverter messageConverter;

	private ConversionService conversionService;

	@BeforeEach
	public void before() {
		List<MessageConverter> messageConverters = new ArrayList<>();
		JsonMapper jsonMapper = new GsonMapper(new Gson());
		messageConverters.add(new JsonMessageConverter(jsonMapper));
		messageConverters.add(new ByteArrayMessageConverter());
		messageConverters.add(new StringMessageConverter());
		this.messageConverter = new CompositeMessageConverter(messageConverters);

		this.conversionService = new DefaultConversionService();
	}

	@Test
	public void testCachingOfFunction() {
		Echo function = new Echo();
		FunctionRegistration<Echo> registration = new FunctionRegistration<>(
				function, "echo").type(FunctionType.of(Echo.class));
		SimpleFunctionRegistry catalog = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));
		catalog.register(registration);

		FunctionInvocationWrapper instanceA = catalog.lookup("echo", "application/json");
		FunctionInvocationWrapper instanceb = catalog.lookup("echo", "text/plain");
		FunctionInvocationWrapper instanceC = catalog.lookup("echo", "foo/bar");

		assertThat(instanceA).isSameAs(instanceb).isSameAs(instanceC);
	}

	@Test
	public void testNoCachingOfFunction() {
		Echo function = new Echo();
		FunctionRegistration<Echo> registration = new FunctionRegistration<>(
				function, "echo").type(FunctionType.of(Echo.class));
		registration.getProperties().put("singleton", "false");
		SimpleFunctionRegistry catalog = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));
		catalog.register(registration);

		FunctionInvocationWrapper instanceA = catalog.lookup("echo", "application/json");
		FunctionInvocationWrapper instanceb = catalog.lookup("echo", "text/plain");
		FunctionInvocationWrapper instanceC = catalog.lookup("echo", "foo/bar");

		assertThat(instanceA).isNotSameAs(instanceb).isNotSameAs(instanceC);
	}

	@Test
	public void testSCF768() {
		ResolvableType map = ResolvableType.forClassWithGenerics(Map.class, String.class, Person.class);
		Type functionType = ResolvableType.forClassWithGenerics(Function.class, map, ResolvableType.forClass(String.class)).getType();

		Function<Map<String, Person>, String> function = persons -> {
			for (Entry<String, Person> entry : persons.entrySet()) {
				assertThat(entry.getValue().getName()).isNotEmpty(); // would fail if value would not be converted to Person
			}
			return persons.toString();
		};

		FunctionRegistration<Function<Map<String, Person>, String>> registration = new FunctionRegistration<>(
				function, "echo").type(FunctionType.of(functionType));
		SimpleFunctionRegistry catalog = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));
		catalog.register(registration);

		FunctionInvocationWrapper lookedUpFunction = catalog.lookup("echo");
		String result = (String) lookedUpFunction.apply("{\"ricky\":{\"name\":\"ricky\"}}");
		assertThat(result).isEqualTo("{ricky=ricky}");
	}

	@Test
	public void testSCF640() {
		Echo function = new Echo();
		FunctionRegistration<Echo> registration = new FunctionRegistration<>(
				function, "echo").type(FunctionType.of(Echo.class));
		SimpleFunctionRegistry catalog = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));
		catalog.register(registration);

		FunctionInvocationWrapper lookedUpFunction = catalog.lookup("echo");
		Object result = lookedUpFunction.apply("{\"HELLO\":\"WORLD\"}");
		assertThat(result).isNotInstanceOf(Message.class);
		assertThat(result).isEqualTo("{\"HELLO\":\"WORLD\"}");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSCF762() {
		SimpleFunctionRegistry catalog = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));

		FunctionRegistration<UpperCase> reg1 = new FunctionRegistration<>(
				new UpperCase(), "uppercase").type(FunctionType.of(UpperCase.class));
		catalog.register(reg1);
		//
		FunctionRegistration<UpperCaseMessage> reg2 = new FunctionRegistration<>(
				new UpperCaseMessage(), "uppercaseMessage").type(FunctionType.of(UpperCaseMessage.class));
		catalog.register(reg2);
		//
		FunctionRegistration<StringArrayFunction> reg3 = new FunctionRegistration<>(
				new StringArrayFunction(), "stringArray").type(FunctionType.of(StringArrayFunction.class));
		catalog.register(reg3);
		//
		FunctionRegistration<TypelessFunction> reg4 = new FunctionRegistration<>(
				new TypelessFunction(), "typeless").type(FunctionType.of(TypelessFunction.class));
		catalog.register(reg4);
		//
		FunctionRegistration<ByteArrayFunction> reg5 = new FunctionRegistration<>(
				new ByteArrayFunction(), "typeless").type(FunctionType.of(ByteArrayFunction.class));
		catalog.register(reg5);
		//
		FunctionRegistration<StringListFunction> reg6 = new FunctionRegistration<>(
				new StringListFunction(), "stringList").type(FunctionType.of(StringListFunction.class));
		catalog.register(reg6);

		Message<String> collectionMessage = MessageBuilder.withPayload("[\"ricky\", \"julien\", \"bubbles\"]").build();
		Message<String> singleValueMessage = MessageBuilder.withPayload("\"ricky\"").build();

		FunctionInvocationWrapper lookedUpFunction = catalog.lookup("uppercase", "application/json");
		Object result = lookedUpFunction.apply(singleValueMessage);
		assertThat(result).isInstanceOf(Message.class);
		assertThat(((Message<byte[]>) result).getPayload()).isEqualTo("\"RICKY\"".getBytes());

		result = lookedUpFunction.apply(collectionMessage);
		assertThat(result).isInstanceOf(Flux.class);
		List<Message<byte[]>> collectionIfResults = Flux.from((Publisher<Message<byte[]>>) result).collectList().block();
		assertThat(collectionIfResults.size()).isEqualTo(3);
		assertThat(collectionIfResults.get(0).getPayload()).isEqualTo("\"RICKY\"".getBytes());
		assertThat(collectionIfResults.get(1).getPayload()).isEqualTo("\"JULIEN\"".getBytes());

		lookedUpFunction = catalog.lookup("typeless", "application/json");
		result = lookedUpFunction.apply(singleValueMessage);
		assertThat(result).isInstanceOf(Message.class);
		assertThat(((Message<byte[]>) result).getPayload()).isEqualTo("\"ricky\"".getBytes());

		result = lookedUpFunction.apply(collectionMessage);
		assertThat(result).isInstanceOf(Message.class);
		assertThat(((Message<byte[]>) result).getPayload()).isEqualTo("[\"ricky\", \"julien\", \"bubbles\"]".getBytes());


		lookedUpFunction = catalog.lookup("stringArray", "application/json");
		result = lookedUpFunction.apply(singleValueMessage);
		assertThat(result).isInstanceOf(Message.class);
		assertThat(((Message<byte[]>) result).getPayload()).isEqualTo("[\"ricky\"]".getBytes());

		result = lookedUpFunction.apply(collectionMessage);
		assertThat(result).isInstanceOf(Message.class);
		assertThat(((Message<byte[]>) result).getPayload()).isEqualTo("[ricky, julien, bubbles]".getBytes());


		lookedUpFunction = catalog.lookup("stringList", "application/json");
		result = lookedUpFunction.apply(singleValueMessage);
		assertThat(result).isInstanceOf(Message.class);
		assertThat(((Message<byte[]>) result).getPayload()).isEqualTo("[\"ricky\"]".getBytes());

		result = lookedUpFunction.apply(collectionMessage);
		assertThat(result).isInstanceOf(Message.class);
		System.out.println(new String(((Message<byte[]>) result).getPayload()));
		assertThat(((Message<byte[]>) result).getPayload()).isEqualTo("[ricky, julien, bubbles]".getBytes());

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSCF588() {

		UpperCase function = new UpperCase();
		FunctionRegistration<UpperCase> registration = new FunctionRegistration<>(
				function, "foo").type(FunctionType.of(UppercaseFunction.class));
		SimpleFunctionRegistry catalog = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));
		catalog.register(registration);

		FunctionInvocationWrapper lookedUpFunction = catalog.lookup("uppercase");
		Message<String> message = MessageBuilder.withPayload("hello")
				.setHeader("lambda-runtime-aws-request-id", UUID.randomUUID())
				.build();
		Object result = lookedUpFunction.apply(message);
		assertThat(result).isInstanceOf(Message.class);
		assertThat(((Message<String>) result).getPayload()).isEqualTo("HELLO");
	}

	@Test
	public void testFunctionLookup() {

		TestFunction function = new TestFunction();
		FunctionRegistration<TestFunction> registration = new FunctionRegistration<>(
				function, "foo").type(FunctionType.of(TestFunction.class));
		SimpleFunctionRegistry catalog = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));
		catalog.register(registration);

		//FunctionInvocationWrapper lookedUpFunction = catalog.lookup("hello");
		FunctionInvocationWrapper lookedUpFunction = catalog.lookup("hello");
		assertThat(lookedUpFunction).isNotNull(); // because we only have one and can look it up with any name
		FunctionRegistration<TestFunction> registration2 = new FunctionRegistration<>(
				function, "foo2").type(FunctionType.of(TestFunction.class));
		catalog.register(registration2);
		lookedUpFunction = catalog.lookup("hello");
		assertThat(lookedUpFunction).isNull();
	}



	@Test
	public void testFunctionComposition() {
		FunctionRegistration<UpperCase> upperCaseRegistration = new FunctionRegistration<>(
				new UpperCase(), "uppercase").type(FunctionType.of(UpperCase.class));
		FunctionRegistration<Reverse> reverseRegistration = new FunctionRegistration<>(
				new Reverse(), "reverse").type(FunctionType.of(Reverse.class));
		SimpleFunctionRegistry catalog = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));
		catalog.register(upperCaseRegistration);
		catalog.register(reverseRegistration);

		Function<Flux<String>, Flux<String>> lookedUpFunction = catalog
				.lookup("uppercase|reverse");
		assertThat(lookedUpFunction).isNotNull();

		Flux flux = lookedUpFunction.apply(Flux.just("star"));
		flux.subscribe(v -> {
			System.out.println(v);
		});

//		assertThat(lookedUpFunction.apply(Flux.just("star")).blockFirst())
//				.isEqualTo("RATS");
	}

	@Test
	@Disabled
	public void testFunctionCompositionImplicit() {
		FunctionRegistration<Words> wordsRegistration = new FunctionRegistration<>(
				new Words(), "words").type(FunctionType.of(Words.class));
		FunctionRegistration<Reverse> reverseRegistration = new FunctionRegistration<>(
				new Reverse(), "reverse").type(FunctionType.of(Reverse.class));
		FunctionRegistry catalog = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));
		catalog.register(wordsRegistration);
		catalog.register(reverseRegistration);

		// There's only one function, we should be able to leave that blank
		Supplier<String> lookedUpFunction = catalog.lookup("words|");

		assertThat(lookedUpFunction).isNotNull();
		assertThat(lookedUpFunction.get()).isEqualTo("olleh");
	}

	@Test
	@Disabled
	public void testFunctionCompletelyImplicitComposition() {
		FunctionRegistration<Words> wordsRegistration = new FunctionRegistration<>(
				new Words(), "words").type(FunctionType.of(Words.class));
		FunctionRegistration<Reverse> reverseRegistration = new FunctionRegistration<>(
				new Reverse(), "reverse").type(FunctionType.of(Reverse.class));
		SimpleFunctionRegistry catalog = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));
		catalog.register(wordsRegistration);
		catalog.register(reverseRegistration);

		// There's only one function, we should be able to leave that blank
		Supplier<Flux<String>> lookedUpFunction = catalog.lookup("|");

		assertThat(lookedUpFunction).isNotNull();
		assertThat(lookedUpFunction.get().blockFirst()).isEqualTo("olleh");
	}

	@Test
	public void testFunctionCompositionExplicit() {
		FunctionRegistration<Words> wordsRegistration = new FunctionRegistration<>(
				new Words(), "words").type(FunctionType.of(Words.class));
		FunctionRegistration<Reverse> reverseRegistration = new FunctionRegistration<>(
				new Reverse(), "reverse").type(FunctionType.of(Reverse.class));
		SimpleFunctionRegistry catalog = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));
		catalog.register(wordsRegistration);
		catalog.register(reverseRegistration);

		Supplier<String> lookedUpFunction = catalog.lookup("words|reverse");

		assertThat(lookedUpFunction).isNotNull();
		assertThat(lookedUpFunction.get()).isEqualTo("olleh");
	}

	@Test
	public void testFunctionCompositionWithMessages() {
		FunctionRegistration<UpperCaseMessage> upperCaseRegistration = new FunctionRegistration<>(
				new UpperCaseMessage(), "uppercase")
						.type(FunctionType.of(UpperCaseMessage.class));
		FunctionRegistration<ReverseMessage> reverseRegistration = new FunctionRegistration<>(
				new ReverseMessage(), "reverse")
						.type(FunctionType.of(ReverseMessage.class));
		SimpleFunctionRegistry catalog = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));
		catalog.register(upperCaseRegistration);
		catalog.register(reverseRegistration);

		Function<Flux<Message<String>>, Flux<Message<String>>> lookedUpFunction = catalog
				.lookup("uppercase|reverse");

		assertThat(lookedUpFunction).isNotNull();
		assertThat(lookedUpFunction
				.apply(Flux.just(MessageBuilder.withPayload("star").build())).blockFirst()
				.getPayload()).isEqualTo("RATS");
	}

	@Test
	public void testFunctionCompositionMixedMessages() {
		FunctionRegistration<UpperCaseMessage> upperCaseRegistration = new FunctionRegistration<>(
				new UpperCaseMessage(), "uppercase")
						.type(FunctionType.of(UpperCaseMessage.class));
		FunctionRegistration<Reverse> reverseRegistration = new FunctionRegistration<>(
				new Reverse(), "reverse").type(FunctionType.of(Reverse.class));
		SimpleFunctionRegistry catalog = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));
		catalog.register(upperCaseRegistration);
		catalog.register(reverseRegistration);

		Function<Message<String>, String> lookedUpFunction = catalog
				.lookup("uppercase|reverse");

		assertThat(lookedUpFunction).isNotNull();
		String result = lookedUpFunction.apply(MessageBuilder.withPayload("star").setHeader("foo", "bar").build());
		assertThat(result).isEqualTo("RATS");
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testReactiveFunctionMessages() {
		FunctionRegistration<ReactiveFunction> registration = new FunctionRegistration<>(new ReactiveFunction(), "reactive")
			.type(FunctionType.of(ReactiveFunction.class));

		SimpleFunctionRegistry catalog = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));
		catalog.register(registration);

		Function lookedUpFunction = catalog.lookup("reactive");

		assertThat(lookedUpFunction).isNotNull();
		Flux<List<String>> result = (Flux<List<String>>) lookedUpFunction
			.apply(Flux.just(MessageBuilder
				.withPayload("[{\"name\":\"item1\"},{\"name\":\"item2\"}]")
				.setHeader(MessageHeaders.CONTENT_TYPE, "application/json")
				.build()
			));

		Assertions.assertIterableEquals(result.blockFirst(), Arrays.asList("item1", "item2"));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testWithCustomMessageConverter() {
		FunctionCatalog catalog = this.configureCatalog(CustomConverterConfiguration.class);
		Function function = catalog.lookup("func");
		Object result = function.apply(MessageBuilder.withPayload("Jim Lahey").setHeader(MessageHeaders.CONTENT_TYPE, "text/person").build());
		assertThat(result).isEqualTo("Jim Lahey");
	}

	@Test
	public void lookup() {
		SimpleFunctionRegistry functionRegistry = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));
		FunctionInvocationWrapper function = functionRegistry.lookup("uppercase");
		assertThat(function).isNull();

		Function userFunction = uppercase();
		FunctionRegistration functionRegistration = new FunctionRegistration(userFunction, "uppercase")
				.type(FunctionType.from(String.class).to(String.class));
		functionRegistry.register(functionRegistration);

		function = functionRegistry.lookup("uppercase");
		assertThat(function).isNotNull();
	}


	@Test
	public void lookupDefaultName() {
		SimpleFunctionRegistry functionRegistry = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));
		Function userFunction = uppercase();
		FunctionRegistration functionRegistration = new FunctionRegistration(userFunction, "uppercase")
				.type(FunctionType.from(String.class).to(String.class));
		functionRegistry.register(functionRegistration);

		FunctionInvocationWrapper function = functionRegistry.lookup("");
		assertThat(function).isNotNull();
	}

	@SuppressWarnings("unchecked")
	@Test
	public void lookupWithCompositionFunctionAndConsumer() {
		SimpleFunctionRegistry functionRegistry = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));

		Object userFunction = uppercase();
		FunctionRegistration functionRegistration = new FunctionRegistration(userFunction, "uppercase")
				.type(FunctionType.from(String.class).to(String.class));
		functionRegistry.register(functionRegistration);

		userFunction = consumer();
		functionRegistration = new FunctionRegistration(userFunction, "consumer")
				.type(ResolvableType.forClassWithGenerics(Consumer.class, Integer.class).getType());
		functionRegistry.register(functionRegistration);

		FunctionInvocationWrapper functionWrapper = functionRegistry.lookup("uppercase|consumer");

		functionWrapper.apply("123");
	}

	@Test
	public void lookupWithReactiveConsumer() {
		SimpleFunctionRegistry functionRegistry = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));

		Object userFunction = reactiveConsumer();

		FunctionRegistration functionRegistration = new FunctionRegistration(userFunction, "reactiveConsumer")
				.type(ResolvableType.forClassWithGenerics(Consumer.class, ResolvableType.forClassWithGenerics(Flux.class, Integer.class)).getType());
		functionRegistry.register(functionRegistration);

		FunctionInvocationWrapper functionWrapper = functionRegistry.lookup("reactiveConsumer");

		functionWrapper.apply("123");
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testHeaderEnricherFunction() {
		FunctionRegistration<HeaderEnricherFunction> registration =
			new FunctionRegistration<>(new HeaderEnricherFunction(), "headerEnricher")
				.type(FunctionType.of(HeaderEnricherFunction.class));
		SimpleFunctionRegistry catalog = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
			new JacksonMapper(new ObjectMapper()));
		catalog.register(registration);
		Function<Message<?>, Message<?>> function = catalog.lookup("headerEnricher");
		Message<?> message =
			function.apply(MessageBuilder.withPayload("hello").setHeader("original", "originalValue")
				.build());
		assertThat(message.getHeaders().get("original")).isEqualTo("newValue");
	}

	@Test
	public void testReactiveMonoSupplier() {
		FunctionRegistration<ReactiveMonoGreeter> registration = new FunctionRegistration<>(new ReactiveMonoGreeter(),
				"greeter").type(FunctionType.of(ReactiveMonoGreeter.class));
		SimpleFunctionRegistry catalog = new SimpleFunctionRegistry(this.conversionService, this.messageConverter,
				new JacksonMapper(new ObjectMapper()));
		catalog.register(registration);
		FunctionInvocationWrapper function = catalog.lookup("greeter");
		assertThat(FunctionTypeUtils.isMono(function.getOutputType()));
	}


	public Function<String, String> uppercase() {
		return v -> v.toUpperCase();
	}


	public Function<Object, Integer> hash() {
		return v -> v.hashCode();
	}

	public Supplier<Integer> supplier() {
		return () -> 4;
	}

	public Consumer<Integer> consumer() {
		return System.out::println;
	}

	public Consumer<Flux<Integer>> reactiveConsumer() {
		return flux -> flux.subscribe(v -> {
			System.out.println(v);
		});
	}

	private FunctionCatalog configureCatalog(Class<?>... configClass) {
		ApplicationContext context = new SpringApplicationBuilder(configClass)
				.run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true");
		FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
		return catalog;
	}

	@EnableAutoConfiguration
	private static class CustomConverterConfiguration {
		@Bean
		public MessageConverter stringToPersonConverter() {
			return new AbstractMessageConverter(MimeType.valueOf("text/person")) {
				@Override
				protected Object convertFromInternal(Message<?> message, Class<?> targetClass, @Nullable Object conversionHint) {
					String payload =  message.getPayload() instanceof byte[] ? new String((byte[]) message.getPayload()) : (String) message.getPayload();
					Person person = new Person();
					person.setName(payload);
					return person;
				}

				@Override
				protected boolean canConvertFrom(Message<?> message, @Nullable Class<?> targetClass) {
					return supportsMimeType(message.getHeaders()) && Person.class.isAssignableFrom(targetClass) && (
							message.getPayload() instanceof String || message.getPayload() instanceof byte[]);
				}

				@Override
				public Object convertToInternal(Object rawPayload, MessageHeaders headers, Object conversionHint) {
					return rawPayload.toString();
				}

				@Override
				protected boolean canConvertTo(Object payload, @Nullable MessageHeaders headers) {
					return true;
				}

				@Override
				protected boolean supports(Class<?> clazz) {
					throw new UnsupportedOperationException();
				}
			};
		}

		@Bean
		public Function<Person, String> func() {
			return person -> person.getName();
		}
	}

	public static class Person {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return this.name;
		}
	}

	private static class Words implements Supplier<String> {

		@Override
		public String get() {
			return "hello";
		}

	}

	private static class UpperCase implements Function<String, String> {

		@Override
		public String apply(String t) {
			return t.toUpperCase();
		}

	}

	private static class Echo implements Function<Object, Object> {

		@Override
		public Object apply(Object t) {
			return t;
		}

	}

	private static class UpperCaseMessage
			implements Function<Message<String>, Message<String>> {

		@Override
		public Message<String> apply(Message<String> t) {
			return MessageBuilder.withPayload(t.getPayload().toUpperCase())
					.copyHeaders(t.getHeaders()).build();
		}

	}

	private static class Reverse implements Function<String, String> {

		@Override
		public String apply(String t) {
			return new StringBuilder(t).reverse().toString();
		}

	}

	private static class ReverseMessage
			implements Function<Message<String>, Message<String>> {

		@Override
		public Message<String> apply(Message<String> t) {
			return MessageBuilder
					.withPayload(new StringBuilder(t.getPayload()).reverse().toString())
					.copyHeaders(t.getHeaders()).build();
		}

	}

	private static class TestFunction implements Function<Integer, String> {

		@Override
		public String apply(Integer t) {
			return "i=" + t;
		}

	}

	private static class ReactiveFunction implements Function<Flux<Message<List<Person>>>, Flux<List<String>>> {

		@Override
		public Flux<List<String>> apply(Flux<Message<List<Person>>> listFlux) {
			return listFlux
				.map(Message::getPayload)
				.map(lst -> lst.stream().map(Person::getName).collect(Collectors.toList()));
		}
	}

	private static class ReactiveMonoGreeter implements Supplier<Mono<Message<String>>> {

		@Override
		public Mono<Message<String>> get() {
			return Mono.just(MessageBuilder.withPayload("hello").build());
		}

	}

	private static class HeaderEnricherFunction implements Function<Message<?>, Message<?>> {

		@Override
		public Message<?> apply(Message<?> message) {
			return MessageBuilder.withPayload(message.getPayload()).setHeader("original", "newValue")
				.build();
		}
	}

	private static class StringArrayFunction implements Function<String[], String> {
		@Override
		public String apply(String[] t) {
			return Arrays.asList(t).toString();
		}
	}

	private static class StringListFunction implements Function<List<String>, String> {
		@Override
		public String apply(List<String> t) {
			return t.toString();
		}
	}

	private static class TypelessFunction implements Function<Object, String> {
		@Override
		public String apply(Object t) {
			return t.toString();
		}
	}

	private static class ByteArrayFunction implements Function<byte[], String> {
		@Override
		public String apply(byte[] t) {
			return new String(t);
		}
	}

}
