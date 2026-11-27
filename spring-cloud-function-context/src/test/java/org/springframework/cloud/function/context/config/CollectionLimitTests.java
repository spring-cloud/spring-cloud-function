/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.cloud.function.context.config;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the collection size limit of the default Jackson mapper.
 *
 * @author Oleg Zhurakousky
 */
public class CollectionLimitTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(
				ContextFunctionCatalogAutoConfiguration.class, JacksonAutoConfiguration.class));

	private static String array(int size) {
		return IntStream.range(0, size).mapToObj(i -> "\"v" + i + "\"").collect(Collectors.joining(",", "[", "]"));
	}

	private static <T> T read(JsonMapper mapper, String json, Class<T> type) {
		return mapper.fromJson(json, type);
	}

	@Test
	public void requestedCollectionTypeIsPreserved() {
		this.runner.run(context -> {
			JsonMapper mapper = context.getBean(JsonMapper.class);
			String json = "[\"b\",\"a\",\"c\",\"a\"]";
			assertThat(mapper.<Object>fromJson(json, new ParameterizedTypeReference<Set<String>>() { }.getType()))
				.isInstanceOf(Set.class).isEqualTo(Set.of("a", "b", "c"));
			assertThat(mapper.<Object>fromJson(json, new ParameterizedTypeReference<LinkedHashSet<String>>() { }.getType()))
				.isInstanceOf(LinkedHashSet.class)
				.asInstanceOf(InstanceOfAssertFactories.collection(String.class))
				.containsExactly("b", "a", "c");
			assertThat(mapper.<Object>fromJson(json, new ParameterizedTypeReference<SortedSet<String>>() { }.getType()))
				.isInstanceOf(TreeSet.class)
				.asInstanceOf(InstanceOfAssertFactories.collection(String.class))
				.containsExactly("a", "b", "c");
			assertThat(mapper.<Object>fromJson(json, new ParameterizedTypeReference<LinkedList<String>>() { }.getType()))
				.isInstanceOf(LinkedList.class);
			assertThat(mapper.<Object>fromJson(json, new ParameterizedTypeReference<ArrayDeque<String>>() { }.getType()))
				.isInstanceOf(ArrayDeque.class);
			assertThat(mapper.<Object>fromJson(json, new ParameterizedTypeReference<Collection<String>>() { }.getType()))
				.isInstanceOf(Collection.class);
		});
	}

	@Test
	public void setPropertyOfPojoIsDeserialized() {
		this.runner.run(context -> {
			JsonMapper mapper = context.getBean(JsonMapper.class);
			Holder holder = read(mapper, "{\"names\":[\"a\",\"b\",\"a\"],\"empty\":[],\"list\":[\"x\"]}", Holder.class);
			assertThat(holder.names).containsExactlyInAnyOrder("a", "b");
			assertThat(holder.empty).isEmpty();
			assertThat(holder.list).containsExactly("x");
		});
	}

	@Test
	public void defaultLimitIsEnforcedWithJacksonException() {
		this.runner.run(context -> {
			JsonMapper mapper = context.getBean(JsonMapper.class);
			assertThat(mapper.<Object>fromJson(array(1000), new ParameterizedTypeReference<List<String>>() { }.getType()))
				.asInstanceOf(InstanceOfAssertFactories.collection(String.class)).hasSize(1000);
			assertThatThrownBy(
					() -> mapper.fromJson(array(1001), new ParameterizedTypeReference<List<String>>() { }.getType()))
				.hasRootCauseInstanceOf(JacksonException.class)
				.hasMessageContaining("1000")
				.hasMessageContaining("spring.cloud.function.json.max-collection-size");
			assertThatThrownBy(
					() -> mapper.fromJson(array(1001), new ParameterizedTypeReference<Set<String>>() { }.getType()))
				.hasRootCauseInstanceOf(JacksonException.class);
		});
	}

	@Test
	public void limitIsConfigurable() {
		this.runner.withPropertyValues("spring.cloud.function.json.max-collection-size=3").run(context -> {
			JsonMapper mapper = context.getBean(JsonMapper.class);
			assertThat(mapper.<Object>fromJson(array(3), new ParameterizedTypeReference<List<String>>() { }.getType()))
				.asInstanceOf(InstanceOfAssertFactories.collection(String.class)).hasSize(3);
			assertThatThrownBy(
					() -> mapper.fromJson(array(4), new ParameterizedTypeReference<List<String>>() { }.getType()))
				.hasRootCauseInstanceOf(JacksonException.class).hasMessageContaining("limit of 3");
		});
		this.runner.withPropertyValues("spring.cloud.function.json.max-collection-size=5000").run(context -> {
			JsonMapper mapper = context.getBean(JsonMapper.class);
			assertThat(mapper.<Object>fromJson(array(4000), new ParameterizedTypeReference<List<String>>() { }.getType()))
				.asInstanceOf(InstanceOfAssertFactories.collection(String.class)).hasSize(4000);
		});
	}

	@Test
	public void limitCanBeDisabled() {
		this.runner.withPropertyValues("spring.cloud.function.json.max-collection-size=0").run(context -> {
			JsonMapper mapper = context.getBean(JsonMapper.class);
			assertThat(mapper.<Object>fromJson(array(2500), new ParameterizedTypeReference<List<String>>() { }.getType()))
				.asInstanceOf(InstanceOfAssertFactories.collection(String.class)).hasSize(2500);
		});
	}

	@Test
	public void everyCollectionHasItsOwnLimitAndNestedValuesAreNotCountedAgainstTheOuterOne() {
		this.runner.withPropertyValues("spring.cloud.function.json.max-collection-size=2").run(context -> {
			JsonMapper mapper = context.getBean(JsonMapper.class);
			// two elements, each holding deeper structures with their own (small) collections
			List<List<Map<String, Object>>> result = mapper.fromJson(
					"[[{\"a\":{\"b\":[1,2]}},{\"c\":\"d\"}],[{\"e\":null},{\"f\":{\"g\":{}}}]]",
					new ParameterizedTypeReference<List<List<Map<String, Object>>>>() { }.getType());
			assertThat(result).hasSize(2);
			assertThat(result.get(0)).hasSize(2);
			assertThat(result.get(1)).hasSize(2);
			assertThatThrownBy(() -> mapper.fromJson("[[1,2,3]]",
					new ParameterizedTypeReference<List<List<Integer>>>() { }.getType()))
				.hasRootCauseInstanceOf(JacksonException.class);
			assertThatThrownBy(() -> mapper.fromJson("[{\"a\":1},{\"b\":2},{\"c\":3}]",
					new ParameterizedTypeReference<List<Map<String, Object>>>() { }.getType()))
				.hasRootCauseInstanceOf(JacksonException.class);
		});
	}

	@Test
	public void nullElementsAreKept() {
		this.runner.run(context -> {
			JsonMapper mapper = context.getBean(JsonMapper.class);
			List<String> result = mapper.fromJson("[\"a\",null,\"b\"]",
					new ParameterizedTypeReference<List<String>>() { }.getType());
			assertThat(result).containsExactly("a", null, "b");
		});
	}

	@Test
	public void customDeserializerOfTheUserMapperIsPreserved() {
		this.runner.withBean("customModule", SimpleModule.class, () -> new SimpleModule()
			.addDeserializer(Marked.class, new ValueDeserializer<Marked>() {
				@Override
				public Marked deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
					return new Marked("custom-" + p.getString());
				}
			})).run(context -> {
				JsonMapper mapper = context.getBean(JsonMapper.class);
				List<Marked> result = mapper.fromJson("[\"x\",\"y\"]",
						new ParameterizedTypeReference<List<Marked>>() { }.getType());
				assertThat(result).extracting(m -> m.value).containsExactly("custom-x", "custom-y");
			});
	}

	public static class Holder {

		public Set<String> names;

		public Set<String> empty;

		public List<String> list;

	}

	public static class Marked {

		final String value;

		Marked(String value) {
			this.value = value;
		}

	}

}
