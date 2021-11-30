/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.function.actuator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;

/**
 *
 * Actuator endpoint to access {@link FunctionCatalog}.
 *
 * @author Oleg Zhurakousky
 * @since 3.2
 */
@Endpoint(id = "functions")
public class FunctionsEndpoint {

	private final FunctionCatalog functionCatalog;

	public FunctionsEndpoint(FunctionCatalog functionCatalog) {
		this.functionCatalog = functionCatalog;
	}

	@ReadOperation
	public Map<String, Map<String, Object>> listAll() {
		Map<String, Map<String, Object>> allFunctions = new TreeMap<>();
		Set<String> names = functionCatalog.getNames(null);
		for (String name : names) {
			FunctionInvocationWrapper function = functionCatalog.lookup(name);
			Map<String, Object> functionMap = new LinkedHashMap<>();
			if (function.isFunction()) {
				functionMap.put("type", "FUNCTION");
				functionMap.put("input-type", this.toSimplePolyIn(function));
				functionMap.put("output-type", this.toSimplePolyOut(function));
			}
			else if (function.isConsumer()) {
				functionMap.put("type", "CONSUMER");
				functionMap.put("input-type", this.toSimplePolyIn(function));
			}
			else {
				functionMap.put("type", "SUPPLIER");
				functionMap.put("output-type", this.toSimplePolyOut(function));
			}
			allFunctions.put(name, functionMap);
		}


		return allFunctions;
	}


	private String toSimplePolyOut(FunctionInvocationWrapper function) {
		return FunctionTypeUtils.getRawType(function.getItemType(function.getOutputType())).getSimpleName().toLowerCase();
	}

	private String toSimplePolyIn(FunctionInvocationWrapper function) {
		return FunctionTypeUtils.getRawType(function.getItemType(function.getInputType())).getSimpleName().toLowerCase();
	}
}
