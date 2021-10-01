/*
 * Copyright 2012-2020 the original author or authors.
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

package example;

import com.microsoft.azure.functions.ExecutionContext;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import java.util.logging.Logger;
import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.cloud.function.adapter.azure.AzureSpringBootRequestHandler;

/**
 * @author Dave Syer
 *
 */
public class MapTests {

	@Test
	public void start() throws Exception {
		AzureSpringBootRequestHandler<String, String> handler = new AzureSpringBootRequestHandler<>(
			Config.class);
		ExecutionContext ec = new ExecutionContext() {
			@Override
			public Logger getLogger() {
				return Logger.getAnonymousLogger();
			}

			@Override
			public String getInvocationId() {
				return "id1";
			}

			@Override
			public String getFunctionName() {
				return "uppercase";
			}
		};

		String result = handler.handleRequest("{\"greeting\": \"hello\",\"name\": \"your_name\"}", ec);
		handler.close();
		assertThat(result).isEqualTo("{\"greeting\":\"HELLO\",\"name\":\"YOUR_NAME\"}");
	}
}
