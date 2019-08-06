/*
 * Copyright 2017-2019 the original author or authors.
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

package org.springframework.cloud.function.deployer;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import org.junit.Test;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
/**
 *
 * @author Oleg Zhurakousky
 * @since 3.0
 */
public class ApplicationContainerTests  {

	@Test
	public void testCustomApplicationContainerWithBootJar() throws Exception {
		String[] args = new String[] {"--spring.cloud.function.location=target/it/bootjar/target/bootjar-0.0.1.BUILD-SNAPSHOT-exec.jar",
				"--spring.cloud.function.function-class=function.example.UpperCaseFunction"};

		JavaInvoker invokerByClass =
				FunctionDeployerBootstrap.instance(args).run(JavaInvoker.class, args);

		assertThat(invokerByClass.uppercaseSimple("bob")).isEqualTo("BOB");
		assertThat(invokerByClass.uppercaseSimple("stacy")).isEqualTo("STACY");
	}

	@Test
	public void testCustomApplicationContainerWithBootAppSimpleTypes() throws Exception {

		String[] args = new String[] {"--spring.cloud.function.location=target/it/bootapp/target/bootapp-0.0.1.BUILD-SNAPSHOT-exec.jar",
				"--spring.cloud.function.function-name=uppercase"};
		JavaInvoker invokerByBean =
				FunctionDeployerBootstrap.instance(args).run(JavaInvoker.class, args);

		Message<byte[]> result = invokerByBean.uppercase(MessageBuilder.withPayload("\"bob\"".getBytes(StandardCharsets.UTF_8)).build());
		assertThat(new String(result.getPayload(), StandardCharsets.UTF_8)).isEqualTo("\"BOB\"");
	}

	@Test
	public void testCustomApplicationContainerWithBootAppWithTypeConversion() throws Exception {

		String[] args = new String[] {"--spring.cloud.function.location=target/it/bootapp/target/bootapp-0.0.1.BUILD-SNAPSHOT-exec.jar",
				"--spring.cloud.function.function-name=uppercasePerson"};

		JavaInvoker invokerByBean =
				FunctionDeployerBootstrap.instance(args).run(JavaInvoker.class, args);

		Message<byte[]> result = invokerByBean.uppercase(MessageBuilder.withPayload("{\"name\":\"bob\",\"id\":1}".getBytes(StandardCharsets.UTF_8)).build());
		assertThat(new String(result.getPayload(), StandardCharsets.UTF_8)).isEqualTo("{\"name\":\"BOB\",\"id\":1}");
	}

	private static class JavaInvoker extends ApplicationContainer {

		JavaInvoker(FunctionCatalog functionCatalog, FunctionInspector functionInspector,
				FunctionProperties functionProperties) {
			super(functionCatalog, functionInspector, functionProperties);
		}

		public Message<byte[]> uppercase(Message<byte[]> input) {
			Function<Message<byte[]>, Message<byte[]>> functon = this.getFunction("application/json");
			return functon.apply(input);
		}

		public String uppercaseSimple(String input) {
			Function<String, String> functon = this.getFunction();
			return functon.apply(input);
		}
	}
}
