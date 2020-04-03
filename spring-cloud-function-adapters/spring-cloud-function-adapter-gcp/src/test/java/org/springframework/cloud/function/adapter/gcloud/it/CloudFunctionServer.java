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

package org.springframework.cloud.function.adapter.gcloud.it;

import com.google.cloud.functions.invoker.runner.Invoker;
import org.junit.rules.ExternalResource;

/**
 * Test rule for starting the Cloud Function server.
 *
 * @author Daniel Zou
 */
public class CloudFunctionServer extends ExternalResource {

	private final int port;

	private final Class<?> adapterClass;

	private final Class<?> springApplicationMainClass;

	private Thread serverThread = null;

	/**
	 * Initializes the Cloud Function Server rule.
	 *
	 * @param port the port to run the server on
	 * @param adapterClass the Cloud Function adapter class being used
	 * @param springApplicationMainClass the Spring main class containing function beans
	 */
	public CloudFunctionServer(int port, Class<?> adapterClass, Class<?> springApplicationMainClass) {
		this.port = port;
		this.adapterClass = adapterClass;
		this.springApplicationMainClass = springApplicationMainClass;
	}

	/**
	 * Starts up the Cloud Function Server.
	 */
	@Override
	protected void before() throws InterruptedException {
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
				// InterruptedException means the server is shutting down
				// at the end of the test (via CTRL+C), so ignore those.
				if (!(e instanceof InterruptedException)) {
					throw new RuntimeException("Failed to start Cloud Functions Server", e);
				}
			}
		};

		this.serverThread = new Thread(startServer);
		this.serverThread.start();

		Thread.sleep(5000);
	}

	@Override
	protected void after() {
		if (this.serverThread != null) {
			this.serverThread.interrupt();
		}

		try {
			Thread.sleep(1000);
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
