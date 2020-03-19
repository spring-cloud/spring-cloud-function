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

package org.springframework.cloud.function.adapter.gcloud.integration;

import com.google.cloud.functions.invoker.runner.Invoker;

/**
 * Helper class to start a Google Cloud Functions server which serves the functions defined in the application
 * context.
 *
 * @author Daniel Zou
 */
public class CloudFunctionServerUtils {

	/**
	 * Creates a thread to start the Cloud Function Server.
	 * @return the newly created thread.
	 */
	public static Thread startServer(int port, Class<?> adapterClass, Class<?> springApplicationMainClass) {

		// Spring uses the System property to detect the correct main class.
		System.setProperty("MAIN_CLASS", springApplicationMainClass.getCanonicalName());

		Runnable startServer = () -> {
			Invoker invoker = new Invoker(
				port,
				adapterClass.getCanonicalName(),
				null,
				springApplicationMainClass.getClassLoader());

			try {
				invoker.startServer();
			}
			catch (Exception e) {
				throw new RuntimeException("Failed to start Cloud Functions Server", e);
			}
		};

		Thread thread = new Thread(startServer);
		thread.start();
		return thread;
	}
}
