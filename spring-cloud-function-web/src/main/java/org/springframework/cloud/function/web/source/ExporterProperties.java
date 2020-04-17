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

package org.springframework.cloud.function.web.source;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.function.context.FunctionProperties;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 *
 */
@ConfigurationProperties(prefix = FunctionProperties.PREFIX + ".web.export")
public class ExporterProperties {

	/**
	 * Flag to indicate that the supplier emits HTTP requests automatically on startup.
	 */
	private boolean autoStartup = true;

	/**
	 * Flag to indicate that extra logging is required for the supplier.
	 */
	private boolean debug = true;

	/**
	 * Properties related to a source of items (via an HTTP GET on startup).
	 */
	private Source source = new Source();

	/**
	 * Properties related to a sink of items (via an HTTP POST on startup).
	 */
	private Sink sink = new Sink();

	/**
	 * Flag to enable the export of a supplier.
	 */
	private boolean enabled;

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isAutoStartup() {
		return this.autoStartup;
	}

	public void setAutoStartup(boolean autoStartup) {
		this.autoStartup = autoStartup;
	}

	public boolean isDebug() {
		return this.debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public Source getSource() {
		return this.source;
	}

	public Sink getSink() {
		return this.sink;
	}

	public static class Source {

		/**
		 * URL template for creating a virtual Supplier from HTTP GET.
		 */
		private String url;

		/**
		 * If the origin url is set, the type of content expected (e.g. a POJO class).
		 * Defaults to String.
		 */
		private Class<?> type;

		/**
		 * Include the incoming headers in the outgoing Supplier. If true the supplier
		 * will be of generic type Message of T equal to the source type.
		 */
		private boolean includeHeaders = true;

		public String getUrl() {
			return this.url;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public Class<?> getType() {
			return this.type == null ? String.class : this.type;
		}

		public void setType(Class<?> type) {
			this.type = type;
		}

		public void setIncludeHeaders(boolean includeHeaders) {
			this.includeHeaders = includeHeaders;
		}

		public boolean isIncludeHeaders() {
			return this.includeHeaders;
		}

	}

	public static class Sink {

		/**
		 * URL template for outgoing HTTP requests. Each item from the supplier is POSTed
		 * to this target.
		 */
		private String url;

		/**
		 * Additional headers to append to the outgoing HTTP requests.
		 */
		private Map<String, String> headers = new LinkedHashMap<>();

		/**
		 * The name of a specific existing Supplier to export from the function catalog.
		 */
		private String name;

		/**
		 * Content type to use when serializing source's output for transport (default 'application/json`).
		 */
		private String contentType = "application/json";

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getUrl() {
			return this.url;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public Map<String, String> getHeaders() {
			return this.headers;
		}

		public String getContentType() {
			return contentType;
		}

		public void setContentType(String contentType) {
			this.contentType = contentType;
		}
	}

}
