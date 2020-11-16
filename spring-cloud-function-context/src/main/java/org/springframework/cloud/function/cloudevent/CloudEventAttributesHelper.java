/*
 * Copyright 2019-2019 the original author or authors.
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

import org.springframework.util.StringUtils;


/**
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
public class CloudEventAttributesHelper extends HashMap<String, Object> {

	/**
	 *
	 */
	private static final long serialVersionUID = 5393610770855366497L;



	CloudEventAttributesHelper(Map<String, Object> headers) {
		super(headers);
	}

	@SuppressWarnings("unchecked")
	public <A> A getId() {
		if (this.containsKey(CloudEventMessageUtils.CANONICAL_ID)) {
			return (A) this.get(CloudEventMessageUtils.CANONICAL_ID);
		}
		else if (this.containsKey(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.ID)) {
			return (A) this.get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.ID);
		}
		return null;
	}

	String getAttributeName(String attributeName) {
		if (this.containsKey(CloudEventMessageUtils.ATTR_PREFIX + attributeName)) {
			return CloudEventMessageUtils.ATTR_PREFIX + attributeName;
		}
		else if (this.containsKey(CloudEventMessageUtils.HTTP_ATTR_PREFIX + attributeName)) {
			return CloudEventMessageUtils.HTTP_ATTR_PREFIX + attributeName;
		}
		return attributeName;
	}

	@SuppressWarnings("unchecked")
	public <A> A getSource() {
		if (this.containsKey(CloudEventMessageUtils.CANONICAL_SOURCE)) {
			return (A) this.get(CloudEventMessageUtils.CANONICAL_SOURCE);
		}
		else if (this.containsKey(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.SOURCE)) {
			return (A) this.get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.SOURCE);
		}
		return (A) this.get(CloudEventMessageUtils.SOURCE);
	}

	@SuppressWarnings("unchecked")
	public <A> A getSpecversion() {
		if (this.containsKey(CloudEventMessageUtils.CANONICAL_SPECVERSION)) {
			return (A) this.get(CloudEventMessageUtils.CANONICAL_SPECVERSION);
		}
		else if (this.containsKey(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.SPECVERSION)) {
			return (A) this.get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.SPECVERSION);
		}
		return (A) this.get(CloudEventMessageUtils.SPECVERSION);
	}

	@SuppressWarnings("unchecked")
	public <A> A getType() {
		if (this.containsKey(CloudEventMessageUtils.CANONICAL_TYPE)) {
			return (A) this.get(CloudEventMessageUtils.CANONICAL_TYPE);
		}
		else if (this.containsKey(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.TYPE)) {
			return (A) this.get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.TYPE);
		}
		return (A) this.get(CloudEventMessageUtils.TYPE);
	}

	@SuppressWarnings("unchecked")
	public <A> A getDataContentType() {
		Object dataContentType;
		if (this.containsKey(CloudEventMessageUtils.CANONICAL_DATACONTENTTYPE)) {
			dataContentType = this.get(CloudEventMessageUtils.CANONICAL_DATACONTENTTYPE);
		}
		else if (this.containsKey(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.DATACONTENTTYPE)) {
			dataContentType = this.get(CloudEventMessageUtils.HTTP_ATTR_PREFIX + CloudEventMessageUtils.DATACONTENTTYPE);
		}
		dataContentType = this.get(CloudEventMessageUtils.DATACONTENTTYPE);
		return (A) dataContentType;
	}

	public void setDataContentType(String datacontenttype) {
		this.put(CloudEventMessageUtils.CANONICAL_DATACONTENTTYPE, datacontenttype);
	}

	@SuppressWarnings("unchecked")
	public <A> A getAtttribute(String name) {
		return (A) this.get(name);
	}

	public boolean isValidCloudEvent() {
		return StringUtils.hasText(this.getId())
				&& StringUtils.hasText(this.getSource())
				&& StringUtils.hasText(this.getSpecversion())
				&& StringUtils.hasText(this.getType());
	}
}
