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


/**
 *
 * @author Oleg Zhurakousky
 * @since 3.1
 */
public class CloudEventAttributes extends HashMap<String, Object> {

	/**
	 *
	 */
	private static final long serialVersionUID = 5393610770855366497L;



	CloudEventAttributes(Map<String, Object> headers) {
		super(headers);
	}

	@SuppressWarnings("unchecked")
	public <A> A getId() {
		return this.containsKey(CloudEventMessageUtils.CE_ID)
				? (A) this.get(CloudEventMessageUtils.CE_ID)
				: (A) this.get(CloudEventMessageUtils.ID);
	}

	@SuppressWarnings("unchecked")
	public <A> A getSource() {
		return this.containsKey(CloudEventMessageUtils.CE_SOURCE)
				? (A) this.get(CloudEventMessageUtils.CE_SOURCE)
				: (A) this.get(CloudEventMessageUtils.SOURCE);
	}

	@SuppressWarnings("unchecked")
	public <A> A getSpecversion() {
		return this.containsKey(CloudEventMessageUtils.CE_SPECVERSION)
				? (A) this.get(CloudEventMessageUtils.CE_SPECVERSION)
				: (A) this.get(CloudEventMessageUtils.SPECVERSION);
	}

	@SuppressWarnings("unchecked")
	public <A> A getType() {
		return this.containsKey(CloudEventMessageUtils.CE_TYPE)
				? (A) this.get(CloudEventMessageUtils.CE_TYPE)
				: (A) this.get(CloudEventMessageUtils.TYPE);
	}

	@SuppressWarnings("unchecked")
	public <A> A getDataContentType() {
		return this.containsKey(CloudEventMessageUtils.CE_DATACONTENTTYPE)
				? (A) this.get(CloudEventMessageUtils.CE_DATACONTENTTYPE)
				: (A) this.get(CloudEventMessageUtils.DATACONTENTTYPE);
	}

	public void setDataContentType(String datacontenttype) {
		this.put(CloudEventMessageUtils.CE_DATACONTENTTYPE, datacontenttype);
	}

	@SuppressWarnings("unchecked")
	public <A> A getAtttribute(String name) {
		return (A) this.get(name);
	}
}
