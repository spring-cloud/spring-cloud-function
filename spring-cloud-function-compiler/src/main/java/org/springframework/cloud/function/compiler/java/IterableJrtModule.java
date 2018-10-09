/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.cloud.function.compiler.java;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.tools.JavaFileObject;

import org.springframework.cloud.function.compiler.java.MemoryBasedJavaFileManager.CompilationInfoCache;

/**
 * Iterable that will produce an iterator that returns classes found
 * in a specific module tree within the Java runtime image that exists in
 * Java 9 and later.
 * 
 * @author Andy Clement
 */
public class IterableJrtModule extends CloseableFilterableJavaFileObjectIterable {

//	private static Logger logger = LoggerFactory.getLogger(IterableJrtModule.class);

	private Path moduleRootPath;
	
	Map<String, JrtFsEnumeration> walkers = new HashMap<>();

	/**
	 * @param compilationInfoCache cache of info that may help accelerate compilation
	 * @param moduleRootPath path to the base of the relevant module within the JRT image
	 * @param packageNameFilter an optional package name if choosing to filter (e.g. com.example)
	 * @param includeSubpackages if true, include results in subpackages of the specified package filter
	 */
	public IterableJrtModule(CompilationInfoCache compilationInfoCache, Path moduleRootPath, String packageNameFilter,
			boolean includeSubpackages) {
		super(compilationInfoCache, packageNameFilter, includeSubpackages);
		this.moduleRootPath = moduleRootPath;
	}

	public Iterator<JavaFileObject> iterator() {
		JrtFsEnumeration jrtFsWalker = walkers.get(moduleRootPath.toString());
		if (jrtFsWalker == null) {
			jrtFsWalker = new JrtFsEnumeration(null, moduleRootPath);
			walkers.put(moduleRootPath.toString(), jrtFsWalker);
		}
		jrtFsWalker.reset();
		return new IteratorOverJrtFsEnumeration(jrtFsWalker);
	}
	
	class IteratorOverJrtFsEnumeration implements Iterator<JavaFileObject> {

		private JavaFileObject nextEntry = null;
		
		private JrtFsEnumeration jrtEnumeration;

		public IteratorOverJrtFsEnumeration(JrtFsEnumeration jrtFsWalker) {
			this.jrtEnumeration = jrtFsWalker;
		}

		private void findNext() {
			if (nextEntry == null) {
				while (jrtEnumeration.hasMoreElements()) {
					JrtEntryJavaFileObject jrtEntry = jrtEnumeration.nextElement();
					String name = jrtEnumeration.getName(jrtEntry);
					if (accept(name)) {
						nextEntry = jrtEntry;
						return;
					}
				}
			}
		}

		public boolean hasNext() {
			findNext();
			return nextEntry != null;
		}

		public JavaFileObject next() {
			findNext();
			if (nextEntry == null) {
				throw new NoSuchElementException();
			}
			JavaFileObject retval = nextEntry;
			nextEntry = null;
			return retval;
		}
	}

	public void close() {
	}

	public void reset() {
		close();
	}
}