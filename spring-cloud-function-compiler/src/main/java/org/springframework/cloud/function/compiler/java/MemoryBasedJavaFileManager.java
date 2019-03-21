/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.cloud.function.compiler.java;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A file manager that serves source code from in memory and ensures output results are kept in memory
 * rather than being flushed out to disk. The JavaFileManager is also used as a lookup mechanism
 * for resolving types.
 *
 * @author Andy Clement
 */
public class MemoryBasedJavaFileManager implements JavaFileManager {

	private static Logger logger = LoggerFactory.getLogger(MemoryBasedJavaFileManager.class);
	
	private CompilationOutputCollector outputCollector;

	private List<CloseableFilterableJavaFileObjectIterable> toClose = new ArrayList<>();

	public MemoryBasedJavaFileManager() {
		outputCollector = new CompilationOutputCollector();
	}

	@Override
	public int isSupportedOption(String option) {
		logger.debug("isSupportedOption({})",option);
		return -1; // Not yet supporting options
	}

	@Override
	public ClassLoader getClassLoader(Location location) {
		// Do not simply return the context classloader as it may get closed and then
		// be unusable for loading any further classes
		logger.debug("getClassLoader({})",location);
		return null; // Do not currently need to load plugins
	}

	@Override
	public Iterable<JavaFileObject> list(Location location, String packageName, Set<Kind> kinds, boolean recurse)
			throws IOException {
		logger.debug("list({},{},{},{})",location,packageName,kinds,recurse);
		CloseableFilterableJavaFileObjectIterable resultIterable = null;
		if (location == StandardLocation.PLATFORM_CLASS_PATH && (kinds==null || kinds.contains(Kind.CLASS))) {
			String sunBootClassPath = System.getProperty("sun.boot.class.path");
			logger.debug("Creating iterable for boot class path: {}",sunBootClassPath);
			resultIterable = new IterableClasspath(sunBootClassPath, packageName, recurse);
			toClose.add(resultIterable);
		} else if (location == StandardLocation.CLASS_PATH && (kinds==null || kinds.contains(Kind.CLASS))) {
			String javaClassPath = System.getProperty("java.class.path");
			logger.debug("Creating iterable for class path: {}",javaClassPath);
			resultIterable = new IterableClasspath(javaClassPath, packageName, recurse);
			toClose.add(resultIterable);
		} else if (location == StandardLocation.SOURCE_PATH) {
			// There are no 'extra sources'
			resultIterable = EmptyIterable.instance;
		} else {
			// Nothing to list
			resultIterable = EmptyIterable.instance;
		}
		return resultIterable;
	}

	@Override
	public boolean hasLocation(Location location) {
		logger.debug("hasLocation({})",location);
		return (location == StandardLocation.SOURCE_PATH ||
				location == StandardLocation.CLASS_PATH ||
				location == StandardLocation.PLATFORM_CLASS_PATH);
	}

	@Override
	public String inferBinaryName(Location location, JavaFileObject file) {
		if (location == StandardLocation.SOURCE_PATH) {
			return null;
		}
		// Kind of ignoring location here... assuming we want basically the FQ type name
		// Example value from getName(): javax/validation/bootstrap/GenericBootstrap.class
		String classname = file.getName().replace('/', '.');
		return classname.substring(0, classname.lastIndexOf(".class"));
	}

	@Override
	public boolean isSameFile(FileObject a, FileObject b) {
		logger.debug("isSameFile({},{})",a,b);
		return a.equals(b);
	}

	@Override
	public boolean handleOption(String current, Iterator<String> remaining) {
		logger.debug("handleOption({},{})",current,remaining);
		return false; // This file manager does not manage any options
	}


	@Override
	public JavaFileObject getJavaFileForInput(Location location, String className, Kind kind) throws IOException {
		logger.debug("getJavaFileForInput({},{},{})",location,className,kind);
		throw new IllegalStateException("Not expected to be used in this context");
	}

	@Override
	public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind, FileObject sibling)
			throws IOException {
		logger.debug("getJavaFileForOutput({},{},{},{})",location,className,kind,sibling);
		// Example parameters: CLASS_OUTPUT, Foo, CLASS, StringBasedJavaSourceFileObject[string:///a/b/c/Foo.java]
		return outputCollector.getJavaFileForOutput(location, className, kind, sibling);
	}

	@Override
	public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
		logger.debug("getFileForInput({},{},{})",location,packageName,relativeName);
		throw new IllegalStateException("Not expected to be used in this context");
	}

	@Override
	public FileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling)
			throws IOException {
		logger.debug("getFileForOutput({},{},{},{})",location,packageName,relativeName,sibling);
		// This can be called when the annotation config processor runs
		// Example parameters: CLASS_OUTPUT, , META-INF/spring-configuration-metadata.json, null
		return outputCollector.getFileForOutput(location, packageName, relativeName, sibling);
	}

	@Override
	public void flush() throws IOException {
	}

	@Override
	public void close() throws IOException {
		for (CloseableFilterableJavaFileObjectIterable closeable: toClose) {
			closeable.close();
		}
	}

	public List<CompiledClassDefinition> getCompiledClasses() {
		return outputCollector.getCompiledClasses();
	}

}