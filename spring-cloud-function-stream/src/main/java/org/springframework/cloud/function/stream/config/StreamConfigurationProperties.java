/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.stream.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Mark Fisher
 */
@ConfigurationProperties(prefix = "spring.cloud.function.stream")
public class StreamConfigurationProperties {

	private Source source = new Source();

	private Sink sink = new Sink();

	private Processor processor = new Processor();

	private boolean shared;

	public static final String ROUTE_KEY = "stream_routekey";

	public Sink getSink() {
		return this.sink;
	}

	public Source getSource() {
		return this.source;
	}

	public Processor getProcessor() {
		return this.processor;
	}

	public boolean isShared() {
		return this.shared;
	}

	public void setShared(boolean shared) {
		this.shared = shared;
	}

	public static class Sink {

		/**
		 * The name of a single consumer to wire up to the input channel. Default is null,
		 * which means all consumers are bound.
		 */
		private String name;

		/**
		 * Flag to be able to switch off binding consumers to input streams.
		 */
		private boolean enabled;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

	}

	public static class Source {

		/**
		 * The name of a single supplier to wire up to the output channel. Default is
		 * null, which means all suppliers are bound.
		 */
		private String name;

		/**
		 * Flag to be able to switch off binding suppliers to output streams. Because of
		 * the underlying behaviour of Spring AMQP etc., sources do not start up and fail
		 * gracefully if the broker is down. So this flag is needed to control the
		 * behaviour if you know the broker is not available and there are suppliers.
		 */
		private boolean enabled;

		/**
		 * Interval to be used for the Duration (in milliseconds) of a non-Flux producing
		 * Supplier. Default is 0, which means the Supplier will only be invoked once.
		 */
		private long interval = 0L;

		public long getInterval() {
			return interval;
		}

		public void setInterval(long interval) {
			this.interval = interval;
		}

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	public static class Processor {

		/**
		 * The name of a single processor to wire up to the input and output channels.
		 * Default is null, which means all functions are bound.
		 */
		private String name;

		/**
		 * Flag to be able to switch off binding consumers to input streams.
		 */
		private boolean enabled;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

	}

	public String getDefaultRoute() {
		return processor.getName() != null ? processor.getName() : sink.getName();
	}

}
