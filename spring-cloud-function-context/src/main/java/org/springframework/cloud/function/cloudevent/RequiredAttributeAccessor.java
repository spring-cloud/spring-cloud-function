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

package org.springframework.cloud.function.cloudevent;

import java.util.Map;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
public class RequiredAttributeAccessor extends CloudEventAttributes {

	/**
	 *
	 */
	private static final long serialVersionUID = 859410409447601477L;

	RequiredAttributeAccessor(Map<String, Object> headers) {
		super(headers);
	}

	public RequiredAttributeAccessor setId(String id) {
		this.put(CloudEventMessageUtils.CE_ID, id);
		return this;
	}

	public RequiredAttributeAccessor setSource(String source) {
		this.put(CloudEventMessageUtils.CE_SOURCE, source);
		return this;
	}

	public RequiredAttributeAccessor setSpecversion(String specversion) {
		this.put(CloudEventMessageUtils.CE_SPECVERSION, specversion);
		return this;
	}

	public RequiredAttributeAccessor setType(String type) {
		this.put(CloudEventMessageUtils.CE_TYPE, type);
		return this;
	}
}
