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

import java.io.File;

import javax.tools.JavaFileObject;

import org.springframework.cloud.function.compiler.java.MemoryBasedJavaFileManager.CompilationInfoCache;

/**
 * Common superclass for iterables that need to handle closing when finished
 * with and that need to handle possible constraints on the values that
 * are iterated over.
 * 
 * @author Andy Clement
 */
public abstract class CloseableFilterableJavaFileObjectIterable implements Iterable<JavaFileObject> {

//	private final static Logger logger = LoggerFactory.getLogger(CloseableFilterableJavaFileObjectIterable.class);

	private final static boolean BOOT_PACKAGING_AWARE = true;
	private final static String BOOT_PACKAGING_PREFIX_FOR_CLASSES = "BOOT-INF/classes/";

	// If set specifies the package the iterator consumer is interested in. Only
	// return results in this package. Will have a trailing separator to speed
	// matching. '/' on its own represents the default package
	protected String packageNameFilter;

	// Indicates whether the consumer of the iterator wants to see classes
	// that are in subpackages of those matching the filter.
	protected boolean includeSubpackages;
	
	protected CompilationInfoCache compilationInfoCache;

	public CloseableFilterableJavaFileObjectIterable(CompilationInfoCache compilationInfoCache, String packageNameFilter, boolean includeSubpackages) {
		if (packageNameFilter!=null && packageNameFilter.contains(File.separator)) {
			throw new IllegalArgumentException("Package name filters should use dots to separate components: "+packageNameFilter);
		}
		this.compilationInfoCache = compilationInfoCache;
		// Normalize filter to forward slashes
		this.packageNameFilter = packageNameFilter==null?null:packageNameFilter.replace('.', '/') + '/';
		this.includeSubpackages = includeSubpackages;
	}

	/**
	 * Used by subclasses to check values against any specified constraints.
	 * 
	 * @param name the name to check against the criteria
	 * @return true if the name is a valid iterator result based on the specified criteria
	 */
	protected boolean accept(String name) {
//		logger.debug("checking {} against constraints packageNameFilter={} includeSubpackages={}",name,packageNameFilter,includeSubpackages);
		if (!name.endsWith(".class")) {
			return false;
		}
		if (packageNameFilter == null) {
			return true;
		}
		boolean accept;
		// Normalize to forward slashes (some jars are producing paths with forward slashes, some with backward slashes)
		name = name.replace('\\', '/');
		if (packageNameFilter.length() == 1 && packageNameFilter.equals("/")) {
			// This is the 'default package' filter representation
			if (name.indexOf('/') == -1) {
				accept = true;
			} else if (BOOT_PACKAGING_AWARE) {
					accept = name.startsWith(BOOT_PACKAGING_PREFIX_FOR_CLASSES) &&
							name.indexOf('/',BOOT_PACKAGING_PREFIX_FOR_CLASSES.length()) == -1;
			}
			return accept;
		}
		if (includeSubpackages == true) {
			accept = name.startsWith(packageNameFilter);
			if (!accept && BOOT_PACKAGING_AWARE) {
				accept = name.startsWith(BOOT_PACKAGING_PREFIX_FOR_CLASSES) &&
						name.indexOf(packageNameFilter)==BOOT_PACKAGING_PREFIX_FOR_CLASSES.length();
			}
		} else {
			accept = name.startsWith(packageNameFilter) && name.indexOf("/",packageNameFilter.length())==-1;
			if (!accept && BOOT_PACKAGING_AWARE) {
				accept = name.startsWith(BOOT_PACKAGING_PREFIX_FOR_CLASSES) &&
						name.indexOf(packageNameFilter)==BOOT_PACKAGING_PREFIX_FOR_CLASSES.length() &&
						name.indexOf("/",BOOT_PACKAGING_PREFIX_FOR_CLASSES.length()+packageNameFilter.length())==-1;
			}
		}
		return accept;
	}

	abstract void close();

	abstract void reset();

}