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

import org.springframework.util.StringUtils;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
public class RequiredAttributeAccessor extends CloudEventAttributesHelper {

	private final String prefixToUse;

	/**
	 *
	 */
	private static final long serialVersionUID = 859410409447601477L;

	RequiredAttributeAccessor(Map<String, Object> headers, String prefixToUse) {
		super(headers);
		this.prefixToUse = prefixToUse;
	}

	RequiredAttributeAccessor(Map<String, Object> headers) {
		this(headers, null);
	}

	public RequiredAttributeAccessor setId(String id) {
		if (StringUtils.hasText(this.prefixToUse)) {
			this.put(this.prefixToUse + CloudEventMessageUtils.ID, id);
		}
		else {
			this.put(this.getAttributeName(CloudEventMessageUtils.ID), id);
		}
		return this;
	}

	public RequiredAttributeAccessor setSource(String source) {
		if (StringUtils.hasText(this.prefixToUse)) {
			this.put(this.prefixToUse + CloudEventMessageUtils.SOURCE, source);
		}
		else {
			this.put(this.getAttributeName(CloudEventMessageUtils.SOURCE), source);
		}
		return this;
	}

	public RequiredAttributeAccessor setSpecversion(String specversion) {
		if (StringUtils.hasText(this.prefixToUse)) {
			this.put(this.prefixToUse + CloudEventMessageUtils.SPECVERSION, specversion);
		}
		else {
			this.put(this.getAttributeName(CloudEventMessageUtils.SPECVERSION), specversion);
		}
		return this;
	}

	public RequiredAttributeAccessor setType(String type) {
		if (StringUtils.hasText(this.prefixToUse)) {
			this.put(this.prefixToUse + CloudEventMessageUtils.TYPE, type);
		}
		else {
			this.put(this.getAttributeName(CloudEventMessageUtils.TYPE), type);
		}
		return this;
	}
}
