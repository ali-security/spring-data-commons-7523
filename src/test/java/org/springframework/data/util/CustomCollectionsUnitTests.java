/*
 * Copyright 2022 the original author or authors.
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
package org.springframework.data.util;

import static org.assertj.core.api.Assertions.*;

import lombok.AllArgsConstructor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.bag.ImmutableBag;
import org.eclipse.collections.api.bag.MutableBag;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.SetIterable;
import org.eclipse.collections.impl.map.immutable.ImmutableUnifiedMap;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

/**
 * Unit tests for {@link CustomCollections}.
 *
 * @author Oliver Drotbohm
 */
@TestInstance(Lifecycle.PER_CLASS)
class CustomCollectionsUnitTests {

	ConfigurableConversionService conversionService = new DefaultConversionService();

	@BeforeAll
	void setUp() {
		CustomCollections.registerConvertersIn(conversionService);
	}

	@TestFactory // #1817
	Stream<DynamicTest> registersVavrCollections() {

		return new CustomCollectionTester()
				.withCollections(io.vavr.collection.Seq.class, io.vavr.collection.Set.class)
				.withMaps(io.vavr.collection.Map.class)
				.withMapImplementations(io.vavr.collection.LinkedHashMap.class, io.vavr.collection.HashMap.class)
				.verify();
	}

	@Test // DATACMNS-1065, #1817
	void conversListToVavr() {

		assertThat(conversionService.canConvert(List.class, io.vavr.collection.Traversable.class)).isTrue();
		assertThat(conversionService.canConvert(List.class, io.vavr.collection.List.class)).isTrue();
		assertThat(conversionService.canConvert(List.class, io.vavr.collection.Set.class)).isTrue();
		assertThat(conversionService.canConvert(List.class, io.vavr.collection.Map.class)).isFalse();

		List<Integer> integers = Arrays.asList(1, 2, 3);

		io.vavr.collection.Traversable<?> result = conversionService.convert(integers,
				io.vavr.collection.Traversable.class);

		assertThat(result).isInstanceOf(io.vavr.collection.List.class);
	}

	@Test // DATACMNS-1065, #1817
	void convertsSetToVavr() {

		assertThat(conversionService.canConvert(Set.class, io.vavr.collection.Traversable.class)).isTrue();
		assertThat(conversionService.canConvert(Set.class, io.vavr.collection.Set.class)).isTrue();
		assertThat(conversionService.canConvert(Set.class, io.vavr.collection.List.class)).isTrue();
		assertThat(conversionService.canConvert(Set.class, io.vavr.collection.Map.class)).isFalse();

		Set<Integer> integers = Collections.singleton(1);

		io.vavr.collection.Traversable<?> result = conversionService.convert(integers,
				io.vavr.collection.Traversable.class);

		assertThat(result).isInstanceOf(io.vavr.collection.Set.class);
	}

	@Test // DATACMNS-1065, #1817
	void convertsMapToVavr() {

		assertThat(conversionService.canConvert(Map.class, io.vavr.collection.Traversable.class)).isTrue();
		assertThat(conversionService.canConvert(Map.class, io.vavr.collection.Map.class)).isTrue();
		assertThat(conversionService.canConvert(Map.class, io.vavr.collection.Set.class)).isFalse();
		assertThat(conversionService.canConvert(Map.class, io.vavr.collection.List.class)).isFalse();

		Map<String, String> map = Collections.singletonMap("key", "value");

		io.vavr.collection.Traversable<?> result = conversionService.convert(map, io.vavr.collection.Traversable.class);

		assertThat(result).isInstanceOf(io.vavr.collection.Map.class);
	}

	@Test // DATACMNS-1065, #1817
	void unwrapsVavrCollectionsToJavaOnes() {

		assertThat(unwrap(io.vavr.collection.List.of(1, 2, 3))).isInstanceOf(List.class);
		assertThat(unwrap(io.vavr.collection.LinkedHashSet.of(1, 2, 3))).isInstanceOf(Set.class);
		assertThat(unwrap(io.vavr.collection.LinkedHashMap.of("key", "value"))).isInstanceOf(Map.class);
	}

	@Test // #1817
	void rejectsInvalidMapType() {
		assertThatIllegalArgumentException().isThrownBy(() -> CustomCollections.getMapBaseType(Object.class));
	}

	@Test // DATACMNS-1065, #1817
	void vavrSeqIsASupportedPaginationReturnType() {
		assertThat(CustomCollections.getPaginationReturnTypes()).contains(io.vavr.collection.Seq.class);
	}

	@Test // DATAJPA-1258. #1817
	void convertsJavaListsToVavrSet() {
		assertThat(conversionService.convert(Collections.singletonList("foo"), io.vavr.collection.Set.class)) //
				.isInstanceOf(io.vavr.collection.Set.class);
	}

	private static Object unwrap(Object source) {
		return CustomCollections.getUnwrappers().stream()
				.reduce(source, (value, mapper) -> mapper.apply(value), (l, r) -> r);
	}

	@AllArgsConstructor
	static class CustomCollectionTester {

		private final Collection<Class<?>> expectedCollections, expectedMaps, collectionImplementations, mapImplementations;

		public CustomCollectionTester() {

			this.expectedCollections = Collections.emptyList();
			this.expectedMaps = Collections.emptyList();
			this.collectionImplementations = Collections.emptyList();
			this.mapImplementations = Collections.emptyList();
		}

		public CustomCollectionTester withCollections(Class<?>... types) {
			return new CustomCollectionTester(Arrays.asList(types), expectedMaps, collectionImplementations,
					mapImplementations);
		}

		public CustomCollectionTester withMaps(Class<?>... types) {
			return new CustomCollectionTester(expectedCollections, Arrays.asList(types), collectionImplementations,
					mapImplementations);
		}

		public CustomCollectionTester withCollectionImplementations(Class<?>... types) {
			return new CustomCollectionTester(expectedCollections, expectedMaps, Arrays.asList(types), mapImplementations);
		}

		public CustomCollectionTester withMapImplementations(Class<?>... types) {
			return new CustomCollectionTester(expectedCollections, expectedMaps, collectionImplementations,
					Arrays.asList(types));
		}

		public Stream<DynamicTest> verify() {

			Stream<DynamicTest> isCollection = DynamicTest.stream(expectedCollections.stream(),
					it -> it.getSimpleName() + " is a collection", it -> {
						assertThat(CustomCollections.isCollection(it)).isTrue();
						assertThat(CustomCollections.getCustomTypes()).contains(it);
					});

			Stream<DynamicTest> isNotMap = DynamicTest.stream(expectedCollections.stream(),
					it -> it.getSimpleName() + " is not a map", it -> {
						assertThat(CustomCollections.isMap(it)).isFalse();
					});

			Stream<DynamicTest> isMap = DynamicTest.stream(expectedMaps.stream(),
					it -> it.getSimpleName() + " is a map", it -> {
						assertThat(CustomCollections.isMap(it)).isTrue();
						assertThat(CustomCollections.getCustomTypes()).contains(it);
					});

			Stream<DynamicTest> isNotCollection = DynamicTest.stream(expectedMaps.stream(),
					it -> it.getSimpleName() + " is not a collection", it -> {
						assertThat(CustomCollections.isCollection(it)).isFalse();
					});

			Stream<DynamicTest> isMapBaseType = DynamicTest.stream(expectedMaps.stream(),
					it -> it.getSimpleName() + " is a map base type", it -> {

						assertThat(CustomCollections.isMapBaseType(it)).isTrue();

						Class<?> expectedBaseType = Map.class.isAssignableFrom(it) ? Map.class : it;

						assertThat(CustomCollections.getMapBaseType(it)).isEqualTo(expectedBaseType);
					});

			Stream<DynamicTest> findsMapBaseType = DynamicTest.stream(mapImplementations.stream(),
					it -> it.getSimpleName() + " is a map implementation type", it -> {

						if (Map.class.isAssignableFrom(it)) {
							assertThat(CustomCollections.getMapBaseType(it)).isEqualTo(Map.class);
						} else {
							assertThat(expectedMaps).contains(CustomCollections.getMapBaseType(it));
						}
					});

			return Stream.of(isCollection, isNotMap, isMap, isNotCollection, isMapBaseType, findsMapBaseType)
					.reduce(Stream::concat)
					.orElse(Stream.empty());
		}
	}
}
