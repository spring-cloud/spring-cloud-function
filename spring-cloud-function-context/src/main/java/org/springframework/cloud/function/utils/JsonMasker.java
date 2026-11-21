/*
 * Copyright 2024-present the original author or authors.
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

package org.springframework.cloud.function.utils;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import tools.jackson.databind.ObjectMapper;

import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.util.ClassUtils;

/**
 * @author Oleg Zhurakousky
 * @author Omer Celik
 */
public final class JsonMasker {

	private static final Log logger = LogFactory.getLog(JsonMasker.class);

	private static final String MASK = "*******";

	private final JacksonMapper mapper = new JacksonMapper(new ObjectMapper());

	private final Set<String> keysToMask = loadKeys(ClassUtils.getDefaultClassLoader());

	private JsonMasker() {
	}

	/**
	 * Returns the shared instance, which is created lazily and safely by the class loader.
	 * @return the shared instance
	 */
	public static JsonMasker INSTANCE() {
		return Holder.INSTANCE;
	}

	public static JsonMasker INSTANCE(Set<String> keysToMask) {
		Holder.INSTANCE.keysToMask.addAll(keysToMask);
		return Holder.INSTANCE;
	}

	public String[] getKeysToMask() {
		return this.keysToMask.toArray(new String[0]);
	}

	public String mask(Object json) {
		if (!JsonMapper.isJsonString(json)) {
			return json instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : (String) json;
		}
		Object tree = this.mapper.fromJson(json, Object.class);
		this.maskIn(tree);
		return new String(this.mapper.toJson(tree), StandardCharsets.UTF_8);
	}

	@SuppressWarnings("unchecked")
	private void maskIn(Object node) {
		if (node instanceof Map<?, ?> map) {
			for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) map).entrySet()) {
				if (this.keysToMask.contains(entry.getKey())) {
					entry.setValue(MASK);
				}
				else {
					this.maskIn(entry.getValue());
				}
			}
		}
		else if (node instanceof Collection<?> collection) {
			collection.forEach(this::maskIn);
		}
	}

	/**
	 * Reads the comma and/or line delimited keys of all {@code META-INF/mask.keys} resources, including
	 * those packaged in jars.
	 */
	static Set<String> loadKeys(ClassLoader classLoader) {
		Set<String> keys = new ConcurrentSkipListSet<>();
		try {
			for (URL url : Collections.list(classLoader.getResources("META-INF/mask.keys"))) {
				try (InputStream in = url.openStream()) {
					for (String key : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("[,\\r\\n]+")) {
						if (!key.isBlank()) {
							keys.add(key.trim());
						}
					}
				}
			}
		}
		catch (Exception e) {
			logger.warn("Failed to load keys to mask. No keys will be masked", e);
		}
		return keys;
	}

	private static final class Holder {

		private static final JsonMasker INSTANCE = new JsonMasker();

	}

}
