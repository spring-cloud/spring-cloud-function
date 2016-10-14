/*
 * Copyright 2016 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function;

import org.springframework.cloud.function.registry.FileSystemFunctionRegistry;

/**
 * @author Mark Fisher
 */
public class FunctionRegistrar {

	public static void main(String[] args) {
		String usage = "USAGE: java FunctionRegistrar [supplier|function|consumer] name lambda";
		if (args.length != 3) {
			System.err.println(usage);
			System.exit(1);
		}
		FileSystemFunctionRegistry registry = new FileSystemFunctionRegistry();
		String type = args[0];
		if ("supplier".equalsIgnoreCase(type)) {
			registry.registerSupplier(args[1], args[2]);
		}
		else if ("function".equalsIgnoreCase(type)) {
			registry.registerFunction(args[1], args[2]);
		}
		else if ("consumer".equalsIgnoreCase(type)) {
			registry.registerConsumer(args[1], args[2]);
		}
		else {
			System.err.println(usage);
			System.exit(1);
		}
	}
}
