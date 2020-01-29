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

package org.springframework.cloud.function.utils;

import java.net.URL;
import java.net.URLClassLoader;

import org.junit.Ignore;
import org.junit.jupiter.api.Test;

public class FunctionClassUtilsTests {

	@Test
	@Ignore
	public void test() throws Exception {
//		String fileName = "/Users/olegz/dev/workspaces/spring/spring-cloud-function/spring-cloud-function-samples/function-sample-aws/target/function-sample-aws-2.0.0.RELEASE-aws.jar";
		String fileName = "/Users/olegz/Downloads/my.spring.functions/my.spring.functions.aws/target/tmp/my.spring.functions.aws-0.0.1-SNAPSHOT.jar";

		URLClassLoader cl = new URLClassLoader(new URL[] {new URL("file:" + fileName)});
		FunctionClassUtils.getStartClass(cl);
	}

}
