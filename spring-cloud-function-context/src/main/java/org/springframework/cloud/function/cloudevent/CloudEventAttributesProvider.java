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


/**
 * Strategy that should be implemented by the user to help with outgoing Cloud Event attributes.
 * <br><br>
 * The provided `attributes` are already initialized with default values, so you can only set the ones that you need.
 * <br>
 * Once implemented, simply configure it as a bean and the framework will invoke it before the outbound Cloud Event Message is finalized.
 *
 * <pre>{@code
 * @Bean
 * public CloudEventAttributesProvider cloudEventAttributesProvider() {
 * 	return attributes -> {
 *		attributes.setSource("https://interface21.com/").setType("com.interface21");
 *	};
 * }}
 * </pre>
 *
 * @author Oleg Zhurakousky
 * @author Dave Syer
 *
 * @since 3.1
 */
@FunctionalInterface
public interface CloudEventAttributesProvider {
	/**
	 *
	 * @param attributes instance of {@link CloudEventAttributes}
	 */
	void generateDefaultCloudEventHeaders(CloudEventAttributes attributes);
}
