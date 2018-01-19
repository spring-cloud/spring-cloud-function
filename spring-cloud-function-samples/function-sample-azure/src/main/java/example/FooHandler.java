/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package example;

import com.microsoft.azure.serverless.functions.ExecutionContext;
import com.microsoft.azure.serverless.functions.annotation.HttpOutput;
import com.microsoft.azure.serverless.functions.annotation.HttpTrigger;

import org.springframework.cloud.function.adapter.azure.AzureSpringBootRequestHandler;

/**
 * @author Soby Chacko
 */
public class FooHandler extends AzureSpringBootRequestHandler<Foo, Bar> {

	@Override
	public @HttpOutput(name = "bar") Bar handleRequest(
			@HttpTrigger(name = "foo", methods = { "get", "post" }) Foo foo,
			ExecutionContext context) {
		context.getLogger().info("Invoking entry point method");
		return (Bar) super.handleRequest(foo, context);
	}

}
