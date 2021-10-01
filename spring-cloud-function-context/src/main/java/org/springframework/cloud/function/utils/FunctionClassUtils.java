/*
 * Copyright 2019-2021 the original author or authors.
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
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.KotlinDetector;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

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

	static Class<?> getStartClass(ClassLoader classLoader) {
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
						Collections.list(classLoader.getResources(JarFile.MANIFEST_NAME)), classLoader);
				if (result == null) {
					result = getStartClass(Collections
							.list(classLoader.getResources("meta-inf/manifest.mf")), classLoader);
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

	private static Class<?> getStartClass(List<URL> list, ClassLoader classLoader) {
		if (logger.isTraceEnabled()) {
			logger.trace("Searching manifests: " + list);
		}
		for (URL url : list) {
			try {
				InputStream inputStream = null;
				Manifest manifest = new Manifest(url.openStream());
				logger.info("Searching for start class in manifest: " + url);
				if (logger.isDebugEnabled()) {
					manifest.write(System.out);
				}
				try {
					String startClassName = manifest.getMainAttributes().getValue("Start-Class");
					if (!StringUtils.hasText(startClassName)) {
						startClassName = manifest.getMainAttributes().getValue("Main-Class");
					}

					if (StringUtils.hasText(startClassName)) {
						Class<?> startClass = ClassUtils.forName(startClassName, classLoader);

						if (KotlinDetector.isKotlinType(startClass)) {
							PathMatchingResourcePatternResolver r = new PathMatchingResourcePatternResolver(classLoader);
							String packageName = startClass.getPackage().getName();
							Resource[] resources = r.getResources("classpath:" + packageName.replace(".", "/") + "/*.class");
							for (int i = 0; i < resources.length; i++) {
								Resource resource = resources[i];
								String className = packageName + "." + (resource.getFilename().replace("/", ".")).replace(".class", "");
								startClass = ClassUtils.forName(className, classLoader);
								if (isSpringBootApplication(startClass)) {
									logger.info("Loaded Main Kotlin Class: " + startClass);
									return startClass;
								}
							}
						}
						else if (isSpringBootApplication(startClass)) {
							logger.info("Loaded Start Class: " + startClass);
							return startClass;
						}
					}
				}
				finally {
					if (inputStream != null) {
						inputStream.close();
					}
				}
			}
			catch (Exception ex) {
				logger.debug("Failed to determine Start-Class in manifest file of " + url, ex);
			}
		}
		return null;
	}

	private static boolean isSpringBootApplication(Class<?> startClass) {
		return startClass.getDeclaredAnnotation(SpringBootApplication.class) != null
				|| startClass.getDeclaredAnnotation(SpringBootConfiguration.class) != null;
	}
}
