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
 * Strategy that should be implemented by the user to help with outgoing Cloud Event
 * headers. <br>
 * <br>
 * NOTE: The provided instance of {@link CloudEventMessageBuilder} may or may not be initialized
 * with default values, so it is the responsibility of the user to ensure that all required Cloud Events
 * attributes are set. That said,  Spring frameworks which utilize this interface
 * will ensure that the provided {@link CloudEventMessageBuilder} is initialized with default values, leaving
 * you responsible to only set the attributes you need. <br>
 * Once implemented, simply configure it as a bean and the framework will invoke it before
 * the outbound Cloud Event Message is finalized.
 *
 * <pre>
 * &#64;Bean
 * public CloudEventHeaderEnricher cloudEventHeaderEnricher() {
 *  return headers -> {
 *   return headers.setSource("https://interface21.com/").setType("com.interface21");
 *  };
 * }
 * </pre>
 *
 * @author Oleg Zhurakousky
 * @author Dave Syer
 * @since 3.1
 */
@FunctionalInterface
public interface CloudEventHeaderEnricher {

	/**
	 * @param attributes instance of {@link CloudEventContext}
	 * @return modified {@link CloudEventContext}
	 */
	/**
	 * Will provide access to an open instance of {@link CloudEventMessageBuilder} so you
	 * can add additional attributes and headers.
	 * @param messageBuilder open instance of {@link CloudEventMessageBuilder}
	 * @return instance of {@link CloudEventMessageBuilder}
	 */
	CloudEventMessageBuilder<?> enrich(CloudEventMessageBuilder<?> messageBuilder);

}
