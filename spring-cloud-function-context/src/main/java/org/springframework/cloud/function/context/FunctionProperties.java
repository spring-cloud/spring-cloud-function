/*
 * Copyright 2019-present the original author or authors.
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

package org.springframework.cloud.function.context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.util.CollectionUtils;

/**
 *
 * @author Oleg Zhurakousky
 * @since 3.0
 *
 */
@ConfigurationProperties(prefix = FunctionProperties.PREFIX)
public class FunctionProperties implements EnvironmentAware, ApplicationContextAware {

	/**
	 * The name prefix for properties defined by this properties class.
	 */
	public final static String PREFIX = "spring.cloud.function";

	/**
	 * Name of the header to be used to instruct function to apply this content type for output conversion.
	 */
	public final static String EXPECT_CONTENT_TYPE_HEADER = "expected-content-type";

	/**
	 * SpEL expression to be used with RoutingFunction.
	 */
	public final static String ROUTING_EXPRESSION = PREFIX + ".routing-expression";

	/**
	 * The name of function definition property.
	 */
	public final static String FUNCTION_DEFINITION = PREFIX + ".definition";

	/**
	 * Key for the proxy name.
	 */
	public final static String PROXY = "proxy";

	/**
	 * Definition of the function to be used. This could be function name (e.g., 'myFunction')
	 * or function composition definition (e.g., 'myFunction|yourFunction')
	 */
	private String definition;

	/**
	 * SpEL expression which should result in function definition (e.g., function name or composition instruction).
	 * NOTE: SpEL evaluation context's root object is the input argument (e.g., Message).
	 */
	private String routingExpression;

	/**
	 * List of functions that are not eligible to be registered in Function Catalog.
	 */
	private final List<String> ineligibleDefinitions;

	private Map<String, FunctionConfigurationProperties> configuration;

	private String expectedContentType;

	private Environment environment;

	private ApplicationContext applicationContext;

	public FunctionProperties() {
		ineligibleDefinitions = new ArrayList<>();
		String[] definitions = new String[] {
				"org.springframework.boot",
				"org.springframework.cloud.function.cloudevent.CloudEventsFunctionExtensionConfiguration",
				"org.springframework.cloud.function.context.config.FunctionsEndpointAutoConfiguration",
				"classLoaderMetrics",
				"jvmMemoryMetrics",
				"jvmInfoMetrics",
				"jvmCompilationMetrics",
				"uptimeMetrics",
				"kotlinToFunctionTransformer",
				"CloudEventsMessageConverterConfiguration"
		};
		ineligibleDefinitions.addAll(Arrays.asList(definitions));
	}

	public Map<String, FunctionConfigurationProperties> getConfiguration() {
		return configuration;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void setConfiguration(Map<String, FunctionConfigurationProperties> configuration) {
		for (Entry<String, FunctionConfigurationProperties> entry : configuration.entrySet()) {
			String propertyX = "spring.cloud.function.configuration." + entry.getKey() + ".input-header-mapping-expression.";
			String propertyY = "spring.cloud.function.configuration." + entry.getKey() + ".inputHeaderMappingExpression.";
			Map<String, Object>  headerMapping = entry.getValue().getInputHeaderMappingExpression();
			if (!CollectionUtils.isEmpty(headerMapping)) {
				for (Object k : headerMapping.keySet()) {
					if (this.environment.containsProperty(propertyX + k) || this.environment.containsProperty(propertyY + k)) {
						Map current = entry.getValue().getInputHeaderMappingExpression();
						if (current.containsKey("0")) {
							((Map) current.get("0")).put(k, headerMapping.get(k));
						}
						else {
							entry.getValue().setInputHeaderMappingExpression(Collections.singletonMap("0", current));
							break;
						}
					}
				}
			}
			propertyX = "spring.cloud.function.configuration." + entry.getKey() + ".output-header-mapping-expression.";
			propertyY = "spring.cloud.function.configuration." + entry.getKey() + ".outputHeaderMappingExpression.";
			headerMapping = entry.getValue().getOutputHeaderMappingExpression();
			if (!CollectionUtils.isEmpty(headerMapping)) {
				for (Object k : headerMapping.keySet()) {
					if (this.environment.containsProperty(propertyX + k) || this.environment.containsProperty(propertyY + k)) {
						Map current = entry.getValue().getOutputHeaderMappingExpression();
						if (current.containsKey("0")) {
							((Map) current.get("0")).put(k, headerMapping.get(k));
						}
						else {
							entry.getValue().setOutputHeaderMappingExpression(Collections.singletonMap("0", current));
							break;
						}
					}
				}
			}
		}

		this.configuration = configuration;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		this.applicationContext = applicationContext;
	}

	public ApplicationContext getApplicationContext() {
		return this.applicationContext;
	}

	public String getDefinition() {
		return definition;
	}

	public void setDefinition(String definition) {
		this.definition = definition;
	}

	public String getRoutingExpression() {
		return routingExpression;
	}

	public void setRoutingExpression(String routingExpression) {
		this.routingExpression = routingExpression;
	}

	public String getExpectedContentType() {
		return this.expectedContentType;
	}

	public void setExpectedContentType(String expectedContentType) {
		this.expectedContentType = expectedContentType;
	}

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	public List<String> getIneligibleDefinitions() {
		return new ArrayList<>(this.ineligibleDefinitions);
	}

	public void setIneligibleDefinitions(List<String> definitions) {
		this.ineligibleDefinitions.addAll(definitions);
	}

	public static class FunctionConfigurationProperties {

		private Map<String, Object> inputHeaderMappingExpression;

		private Map<String, Object> outputHeaderMappingExpression;

		private boolean copyInputHeaders;

		public Map<String, Object> getInputHeaderMappingExpression() {
			return inputHeaderMappingExpression;
		}

		public void setInputHeaderMappingExpression(Map<String, Object> inputHeaderMappingExpression) {
			this.inputHeaderMappingExpression = inputHeaderMappingExpression;
		}

		public Map<String, Object> getOutputHeaderMappingExpression() {
			return outputHeaderMappingExpression;
		}

		public void setOutputHeaderMappingExpression(
				Map<String, Object> outputHeaderMappingExpression) {
			this.outputHeaderMappingExpression = outputHeaderMappingExpression;
		}

		public boolean isCopyInputHeaders() {
			return copyInputHeaders;
		}

		public void setCopyInputHeaders(boolean copyInputHeaders) {
			this.copyInputHeaders = copyInputHeaders;
		}

	}
}
