/*
 * Copyright 2019-present the original author or authors.
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

package org.springframework.cloud.function.deployer.utils;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * A {@link ResourceLoader} that loads {@link MavenResource}s from locations of the format
 * {@literal maven://<coordinates>} where the value for "coordinates" conforms to the rules
 * described on {@link MavenResource#parse(String)} .
 *
 * @author Mark Fisher
 */
public class MavenResourceLoader implements ResourceLoader {

	private static final String URI_SCHEME = "maven";

	private final MavenProperties properties;

	private final ClassLoader classLoader = ClassUtils.getDefaultClassLoader();

	/**
	 * Create a {@link MavenResourceLoader} that uses the provided {@link MavenProperties}.
	 *
	 * @param properties the {@link MavenProperties} to use when instantiating {@link MavenResource}s
	 */
	public MavenResourceLoader(MavenProperties properties) {
		Assert.notNull(properties, "MavenProperties must not be null");
		this.properties = properties;
	}

	/**
	 * Returns a {@link MavenResource} for the provided location.
	 *
	 * @param location the coordinates conforming to the rules described on
	 * {@link MavenResource#parse(String)}. May optionally be preceded by {@value #URI_SCHEME}
	 * followed by a colon and zero or more forward slashes, e.g.
	 * {@literal maven://group:artifact:version}
	 * @return the {@link MavenResource}
	 */
	@Override
	public Resource getResource(String location) {
		Assert.hasText(location, "location is required");
		String coordinates = location.replaceFirst(URI_SCHEME + ":\\/*", "");
		return MavenResource.parse(coordinates, this.properties);
	}

	/**
	 * Returns the {@link ClassLoader} for this ResourceLoader.
	 */
	@Override
	public ClassLoader getClassLoader() {
		return this.classLoader;
	}

}
