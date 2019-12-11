/*
 * Copyright 2019-2019 the original author or authors.
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

import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * General utility class which aggregates various class-level utility functions
 * used by the framework.
 *
 * @author Oleg Zhurakousky
 * @since 3.0.1
 */
public final class FunctionClassUtils {

	private static Log logger = LogFactory.getLog(FunctionClassUtils.class);

	private FunctionClassUtils() {

	}

	/**
	 * Discovers the start class in the currently running application.
	 * The discover search order is 'MAIN_CLASS' environment property,
	 * 'MAIN_CLASS' system property, META-INF/MANIFEST.MF:'Start-Class' attribute,
	 * meta-inf/manifest.mf:'Start-Class' attribute.
	 *
	 * @return instance of Class which represent the start class of the application.
	 */
	public static Class<?> getStartClass() {
		ClassLoader classLoader = FunctionClassUtils.class.getClassLoader();
		return getStartClass(classLoader);
	}

	private static Class<?> getStartClass(ClassLoader classLoader) {
		Class<?> mainClass = null;
		if (System.getenv("MAIN_CLASS") != null) {
			mainClass = ClassUtils.resolveClassName(System.getenv("MAIN_CLASS"), classLoader);
		}
		else if (System.getProperty("MAIN_CLASS") != null) {
			mainClass = ClassUtils.resolveClassName(System.getProperty("MAIN_CLASS"), classLoader);
		}
		else {
			try {
				Class<?> result = getStartClass(
						Collections.list(classLoader.getResources(JarFile.MANIFEST_NAME)));
				if (result == null) {
					result = getStartClass(Collections
							.list(classLoader.getResources("meta-inf/manifest.mf")));
				}
				Assert.notNull(result, "Failed to locate main class");
				mainClass = result;
			}
			catch (Exception ex) {
				throw new IllegalStateException("Failed to discover main class. An attempt was made to discover "
						+ "main class as 'MAIN_CLASS' environment variable, system property as well as "
						+ "entry in META-INF/MANIFEST.MF (in that order).", ex);
			}
		}
		logger.info("Main class: " + mainClass);
		return mainClass;
	}

	private static Class<?> getStartClass(List<URL> list) {
		logger.info("Searching manifests: " + list);
		for (URL url : list) {
			try {
				logger.info("Searching manifest: " + url);

				Manifest manifest;
				InputStream inputStream = null;
				try {
					if ("jar".equals(url.getProtocol())) {
						JarURLConnection jarConnection = (JarURLConnection) url.openConnection();
						manifest = jarConnection.getManifest();
					}
					else {
						manifest = new Manifest(url.openStream());
					}
				}
				finally {
					if (inputStream != null) {
						inputStream.close();
					}
				}

				String startClass = manifest.getMainAttributes().getValue("Start-Class");
				if (startClass != null) {
					return ClassUtils.forName(startClass, FunctionClassUtils.class.getClassLoader());
				}

			}
			catch (Exception ex) {
				logger.debug("Failed to determine Start-Class in manifest file of " + url, ex);
			}
		}
		return null;
	}
}
