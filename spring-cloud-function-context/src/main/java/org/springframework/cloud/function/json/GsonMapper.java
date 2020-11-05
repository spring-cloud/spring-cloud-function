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

package org.springframework.cloud.function.json;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class GsonMapper extends JsonMapper {

	private final Gson gson;

	public GsonMapper(Gson gson) {
		this.gson = gson;
	}

	@Override
	public <T> T toObject(String json, Type type) {
		return this.fromJson(json, type);
	}

	@Override
	public String toString(Object value) {
		return this.gson.toJson(value);
	}

	@Override
	protected <T> T doFromJson(Object json, Type type) {
		T convertedValue = null;
		if (json instanceof byte[]) {
			convertedValue = this.gson.fromJson(new String(((byte[]) json), StandardCharsets.UTF_8), type);
		}
		else if (json instanceof String) {
			convertedValue = this.gson.fromJson((String) json, type);
		}
		else if (json instanceof Reader) {
			convertedValue = this.gson.fromJson((Reader) json, type);
		}
		else if (json instanceof JsonElement) {
			convertedValue = this.gson.fromJson((JsonElement) json, type);
		}
		return convertedValue;
	}

	@Override
	public byte[] toJson(Object value) {
		byte[] jsonBytes = super.toJson(value);
		if (jsonBytes == null) {
			jsonBytes = this.gson.toJson(value).getBytes(StandardCharsets.UTF_8);
		}
		return jsonBytes;
	}

}
