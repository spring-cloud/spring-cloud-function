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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.messaging.MessageHeaders;
import org.springframework.util.StringUtils;


/**
 * Utility class to assist with accessing and setting Cloud Events attributes from {@link MessageHeaders}.
 * <br><br>
 * It is effectively a wrapper over {@link MessageHeaders} which is a {@link Map}.
 * It also provides best effort to both discover the actual attribute name (regardless of the prefix)
 * as well as set appropriate attribute name.
 * <br><br>
 * For example, If there is an attribute `ce-source` or `ce_source` or 'source`, by simply calling getSource()
 * we'll discover it and will return its value.
 * <br>
 * Similar effort will happen during the setting of the attribute. If you provide {@link #prefixToUse} we will
 * use it otherwise we'll attempt to determine based on current execution context which prefix to use.
 *
 * @author Oleg Zhurakousky
 * @author Dave Syer
 *
 * @since 3.1
 */
public class CloudEventAttributes extends HashMap<String, Object> {

	/**
	 *
	 */
	private static final long serialVersionUID = 5393610770855366497L;


	private final String prefixToUse;

	public CloudEventAttributes(Map<String, Object> headers, String prefixToUse) {
		super(headers);
		this.prefixToUse = prefixToUse;
	}

	public CloudEventAttributes(Map<String, Object> headers) {
		this(headers, null);
	}

	public CloudEventAttributes setId(String id) {
		this.setAttribute(CloudEventMessageUtils.ID, id);
		return this;
	}

	public <A> A getId() {
		A id = this.getAttribute(CloudEventMessageUtils.ID);
		if (id instanceof UUID) {
			id = null;
		}
		return id;
	}

	public CloudEventAttributes setSource(String source) {
		this.setAttribute(CloudEventMessageUtils.SOURCE, source);
		return this;
	}

	public <A> A getSource() {
		return this.getAttribute(CloudEventMessageUtils.SOURCE);
	}

	public CloudEventAttributes setSpecversion(String specversion) {
		this.setAttribute(CloudEventMessageUtils.SPECVERSION, specversion);
		return this;
	}

	public <A> A getSpecversion() {
		return this.getAttribute(CloudEventMessageUtils.SPECVERSION);
	}

	public CloudEventAttributes setType(String type) {
		this.setAttribute(CloudEventMessageUtils.TYPE, type);
		return this;
	}

	public <A> A getType() {
		return this.getAttribute(CloudEventMessageUtils.TYPE);
	}

	public CloudEventAttributes setDataContentType(String datacontenttype) {
		this.setAttribute(CloudEventMessageUtils.DATACONTENTTYPE, datacontenttype);
		return this;
	}

	public <A> A getDataContentType() {
		return this.getAttribute(CloudEventMessageUtils.DATACONTENTTYPE);
	}

	public CloudEventAttributes setDataSchema(String dataschema) {
		this.setAttribute(CloudEventMessageUtils.DATASCHEMA, dataschema);
		return this;
	}

	public <A> A getDataSchema() {
		return this.getAttribute(CloudEventMessageUtils.DATASCHEMA);
	}

	public CloudEventAttributes setSubject(String subject) {
		this.setAttribute(CloudEventMessageUtils.SUBJECT, subject);
		return this;
	}

	public <A> A getSubect() {
		return this.getAttribute(CloudEventMessageUtils.SUBJECT);
	}

	public CloudEventAttributes setTime(String time) {
		this.setAttribute(CloudEventMessageUtils.TIME, time);
		return this;
	}

	public <A> A getTime() {
		return this.getAttribute(CloudEventMessageUtils.TIME);
	}

	/**
	 * Will delegate to the underlying {@link Map} returning the value for the requested attribute or null.
	 */
	@SuppressWarnings("unchecked")
	public <A> A getAttribute(String attrName) {
		if (this.containsKey(CloudEventMessageUtils.DEFAULT_ATTR_PREFIX + attrName)) {
			return (A) this.get(CloudEventMessageUtils.DEFAULT_ATTR_PREFIX + attrName);
		}
		else if (this.containsKey(CloudEventMessageUtils.HTTP_ATTR_PREFIX + attrName)) {
			return (A) this.get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + attrName);
		}
		return (A) this.get(attrName);
	}

	/**
	 * Determines if this instance of {@link CloudEventAttributes} represents valid Cloud Event.
	 * This implies that it contains all 4 required attributes (id, source, type & specversion)
	 *
	 * @return true if this instance represents a valid Cloud Event
	 */
	public boolean isValidCloudEvent() {
		return StringUtils.hasText(this.getId())
				&& StringUtils.hasText(this.getSource())
				&& StringUtils.hasText(this.getSpecversion())
				&& StringUtils.hasText(this.getType());
	}

	String getAttributeName(String attributeName) {
		if (this.containsKey(CloudEventMessageUtils.DEFAULT_ATTR_PREFIX + attributeName)) {
			return CloudEventMessageUtils.DEFAULT_ATTR_PREFIX + attributeName;
		}
		else if (this.containsKey(CloudEventMessageUtils.HTTP_ATTR_PREFIX + attributeName)) {
			return CloudEventMessageUtils.HTTP_ATTR_PREFIX + attributeName;
		}
		return attributeName;
	}

	private CloudEventAttributes setAttribute(String attrName, String attrValue) {
		if (StringUtils.hasText(this.prefixToUse)) {
			this.remove(this.getAttributeName(attrName));
			this.put(this.prefixToUse + attrName, attrValue);
		}
		else {
			this.put(this.getAttributeName(attrName), attrValue);
		}
		return this;
	}
}
