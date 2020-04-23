/*
 * Copyright 2020-2020 the original author or authors.
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

package org.springframework.cloud.function.adapter.gcp;

/**
 * An immutable implementation of the Google Cloud Function
 * {@link com.google.cloud.functions.Context} interface.
 *
 * @author Mike Eltsufin
 * @since 3.0.5
 */
public class Context implements com.google.cloud.functions.Context {

	private String eventId;

	private String timestamp;

	private String eventType;

	private String resource;

	public Context() {
	}

	public Context(String eventId, String timestamp, String eventType, String resource) {
		this.eventId = eventId;
		this.timestamp = timestamp;
		this.eventType = eventType;
		this.resource = resource;
	}

	@Override
	public String eventId() {
		return this.eventId;

	}

	@Override
	public String timestamp() {
		return this.timestamp;

	}

	@Override
	public String eventType() {
		return this.eventType;

	}

	@Override
	public String resource() {
		return this.resource;
	}

}
