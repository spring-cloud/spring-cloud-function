/*
 * Copyright 2024-2024 the original author or authors.
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

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.util.ClassUtils;


/**
 * @author Oleg Zhurakousky
 */
public final class JsonMasker {

	private static final Log logger = LogFactory.getLog(JsonMasker.class);

	private static JsonMasker jsonMasker;

	private final JacksonMapper mapper;

	private final Set<String> keysToMask;

	private JsonMasker() {
		this.keysToMask = loadKeys();
		this.mapper = new JacksonMapper(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT));

	}

	public synchronized static JsonMasker INSTANCE() {
		if (jsonMasker == null) {
			jsonMasker = new JsonMasker();
		}
		return jsonMasker;
	}

	public synchronized static JsonMasker INSTANCE(Set<String> keysToMask) {
		INSTANCE().addKeys(keysToMask);
		return jsonMasker;
	}

	public String[] getKeysToMask() {
		return keysToMask.toArray(new String[0]);
	}

	public String mask(Object json) {
		if (!JsonMapper.isJsonString(json)) {
			return (String) json;
		}
		Object map = this.mapper.fromJson(json, Object.class);
		return this.iterate(map);
	}

	@SuppressWarnings({ "unchecked" })
	private String iterate(Object json) {
		if (json instanceof Collection arrayValue) {
			for (Object element : arrayValue) {
				if (element instanceof Map mapElement) {
					for (Map.Entry<String, Object> entry : ((Map<String, Object>) mapElement).entrySet()) {
						this.doMask(entry.getKey(), entry);
					}
				}
			}
		}
		else if (json instanceof Map mapElement) {
			for (Map.Entry<String, Object> entry : ((Map<String, Object>) mapElement).entrySet()) {
				this.doMask(entry.getKey(), entry);
			}
		}
		return new String(this.mapper.toJson(json), StandardCharsets.UTF_8);
	}

	private void doMask(String key, Map.Entry<String, Object> entry) {
		if (this.keysToMask.contains(key)) {
			entry.setValue("*******");
		}
		else if (entry.getValue() instanceof Map) {
			this.iterate(entry.getValue());
		}
		else if (entry.getValue() instanceof Collection) {
			this.iterate(entry.getValue());
		}
	}

	private static Set<String> loadKeys() {
		Set<String> finalKeysToMask = new TreeSet<>();
		try {
			Enumeration<URL> resources = ClassUtils.getDefaultClassLoader().getResources("META-INF/mask.keys");
			while (resources.hasMoreElements()) {
				URI uri = resources.nextElement().toURI();
				List<String> lines = Files.readAllLines(Path.of(uri));
				for (String line : lines) {
					// need to split in case if delimited
					String[] keys = line.split(",");
					for (int i = 0; i < keys.length; i++) {
						finalKeysToMask.add(keys[i].trim());
					}
				}
			}
		}
		catch (Exception e) {
			logger.warn("Failed to load keys to mask. No keys will be masked", e);
		}
		return finalKeysToMask;
	}

	private void addKeys(Set<String> keys) {
		this.keysToMask.addAll(keys);
	}
}
