/*
 * Copyright 2016-2017 the original author or authors.
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

package org.springframework.cloud.function.stream.function;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

import org.junit.Test;

import org.springframework.messaging.Message;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;

/**
 * @author Dave Syer
 *
 */
public class ClassLoaderUtils {

	@Test
	public void fluxIsShared() {
		Class<?> flux = ClassUtils.resolveClassName(Flux.class.getName(),
				createClassLoader());
		assertThat(flux).isEqualTo(Flux.class);
	}

	@Test
	public void messageIsNotShared() {
		Class<?> flux = ClassUtils.resolveClassName(Message.class.getName(),
				createClassLoader());
		assertThat(flux).isNotEqualTo(Message.class);
	}

	@Test(expected = IllegalArgumentException.class)
	public void messageIsNotAvailable() {
		Class<?> flux = ClassUtils.resolveClassName(Message.class.getName(),
				createMinimalClassLoader());
		assertThat(flux).isNotEqualTo(Message.class);
	}

	public static ClassLoader createMinimalClassLoader() {
		ClassLoader base = ClassLoaderUtils.class.getClassLoader();
		try {
			return new URLClassLoader(
					new URL[] { new File("target/test-classes").toURI().toURL() },
					base.getParent());
		}
		catch (MalformedURLException e) {
			throw new IllegalStateException(e);
		}
	}

	public static ClassLoader createClassLoader() {
		URL[] urls = findClassPath();
		if (urls.length == 1) {
			URL[] classpath = extractClasspath(urls[0]);
			if (classpath != null) {
				urls = classpath;
			}
		}
		List<URL> child = new ArrayList<>();
		for (URL url : urls) {
			child.add(url);
		}
		for (URL url : urls) {
			if (isRoot(StringUtils.getFilename(clean(url.toString())))) {
				child.remove(url);
			}
		}
		ClassLoader base = ClassLoaderUtils.class.getClassLoader();
		return new ParentLastURLClassLoader(child.toArray(new URL[0]), base);
	}

	private static URL[] extractClasspath(URL url) {
		// This works for a jar indirection like in surefire and IntelliJ
		if (url.toString().endsWith(".jar")) {
			JarFile jar;
			try {
				jar = new JarFile(new File(url.toURI()));
				String path = jar.getManifest().getMainAttributes()
						.getValue("Class-Path");
				if (path != null) {
					List<URL> result = new ArrayList<>();
					for (String element : path.split(" ")) {
						result.add(new URL(element));
					}
					return result.toArray(new URL[0]);
				}
			}
			catch (Exception e) {
			}
		}
		return null;
	}

	private static String clean(String jar) {
		// This works with fat jars like Spring Boot where the path elements look like
		// jar:file:...something.jar!/.
		return jar.endsWith("!/") ? jar.substring(0, jar.length() - 2) : jar;
	}

	private static URL[] findClassPath() {
		return ((URLClassLoader) ClassLoaderUtils.class.getClassLoader()).getURLs();
	}

	private static boolean isRoot(String file) {
		return file.startsWith("reactor-core") || file.startsWith("reactive-streams");
	}

	private static class ParentLastURLClassLoader extends ClassLoader {
		private ChildURLClassLoader childClassLoader;

		/**
		 * This class allows me to call findClass on a classloader
		 */
		private static class FindClassClassLoader extends ClassLoader {
			public FindClassClassLoader(ClassLoader parent) {
				super(parent);
			}

			@Override
			public Class<?> findClass(String name) throws ClassNotFoundException {
				return super.findClass(name);
			}
		}

		/**
		 * This class delegates (child then parent) for the findClass method for a
		 * URLClassLoader. We need this because findClass is protected in URLClassLoader
		 */
		private static class ChildURLClassLoader extends URLClassLoader {
			private FindClassClassLoader realParent;

			public ChildURLClassLoader(URL[] urls, FindClassClassLoader realParent) {
				super(urls, null);

				this.realParent = realParent;
			}

			@Override
			public Class<?> findClass(String name) throws ClassNotFoundException {
				try {
					// first try to use the URLClassLoader findClass
					return super.findClass(name);
				}
				catch (ClassNotFoundException e) {
					// if that fails, we ask our real parent classloader to load the class
					// (we give up)
					return realParent.loadClass(name);
				}
			}
		}

		public ParentLastURLClassLoader(URL[] urls, ClassLoader parent) {
			super(parent);
			childClassLoader = new ChildURLClassLoader(urls,
					new FindClassClassLoader(this.getParent()));
		}

		@Override
		protected synchronized Class<?> loadClass(String name, boolean resolve)
				throws ClassNotFoundException {
			try {
				// first we try to find a class inside the child classloader
				return childClassLoader.findClass(name);
			}
			catch (ClassNotFoundException e) {
				// didn't find it, try the parent
				return super.loadClass(name, resolve);
			}
		}
	}

}
