/*
 * Copyright 2016 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;

import javax.tools.FileObject;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject.Kind;

/**
 * During compilation instances of this class will collect up the output files from the compilation process.
 * Any kind of file is collected upon but access is only currently provided to retrieve classes produced
 * during compilation. Annotation processors that run may create other kinds of artifact.
 * 
 * @author Andy Clement
 */
public class CompilationOutputCollector {

	private List<InMemoryJavaFileObject> outputFiles = new ArrayList<>();

	/**
	 * Retrieve compiled classes that have been collected since this collector
	 * was built. Due to annotation processing it is possible other source files
	 * or metadata files may be produced during compilation - those are not included
	 * in the returned list.
	 * 
	 * @return list of compiled classes
	 */
	public List<CompiledClassDefinition> getCompiledClasses() {
		List<CompiledClassDefinition> compiledClassDefinitions = new ArrayList<>();
		for (InMemoryJavaFileObject outputFile : outputFiles) {
			if (outputFile.getKind() == Kind.CLASS) {
				CompiledClassDefinition compiledClassDefinition = new CompiledClassDefinition(outputFile.getName(),
						outputFile.getBytes());
				compiledClassDefinitions.add(compiledClassDefinition);
			}
		}
		return compiledClassDefinitions;
	}

	public InMemoryJavaFileObject getJavaFileForOutput(Location location, String className, Kind kind, FileObject sibling) {
		InMemoryJavaFileObject jfo = InMemoryJavaFileObject.getJavaFileObject(location, className, kind, sibling);
		outputFiles.add(jfo);
		return jfo;
	}

	public InMemoryJavaFileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling) {
		InMemoryJavaFileObject ojfo = InMemoryJavaFileObject.getFileObject(location, packageName, relativeName, sibling);
		outputFiles.add(ojfo);
		return ojfo;
	}

}