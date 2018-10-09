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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compile Java source at runtime and load it.
 *
 * @author Andy Clement
 */
public class RuntimeJavaCompiler {

	private JavaCompiler compiler =  ToolProvider.getSystemJavaCompiler();

	private static Logger logger = LoggerFactory.getLogger(RuntimeJavaCompiler.class);

	/**
	 * Compile the named class consisting of the supplied source code. If successful load the class
	 * and return it. Multiple classes may get loaded if the source code included anonymous/inner/local
	 * classes.
	 * @param className the name of the class (dotted form, e.g. com.foo.bar.Goo)
	 * @param classSourceCode the full source code for the class
	 * @param dependencies optional coordinates for dependencies, maven 'maven://groupId:artifactId:version', or 'file:' URIs for local files
	 * @return a CompilationResult that encapsulates what happened during compilation (classes/messages produced)
	 */
	public CompilationResult compile(String className, String classSourceCode, String... dependencies) {
		logger.info("Compiling source for class {} using compiler {}",className,compiler.getClass().getName());

		DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<JavaFileObject>();
		MemoryBasedJavaFileManager fileManager = new MemoryBasedJavaFileManager();
		List<CompilationMessage> resolutionMessages = fileManager.addAndResolveDependencies(dependencies);
		JavaFileObject sourceFile = InMemoryJavaFileObject.getSourceJavaFileObject(className, classSourceCode);

		Iterable<? extends JavaFileObject> compilationUnits = Arrays.asList(sourceFile);
		List<String> options = new ArrayList<>();
		options.add("-source");
		options.add("1.8");
		CompilationTask task = compiler.getTask(null, fileManager , diagnosticCollector, options,  null, compilationUnits);

		boolean success = task.call();
		CompilationResult compilationResult = new CompilationResult(success);
		compilationResult.recordCompilationMessages(resolutionMessages);
		compilationResult.setResolvedAdditionalDependencies(new ArrayList<>(fileManager.getResolvedAdditionalDependencies().values()));

		// If successful there may be no errors but there might be info/warnings
		for (Diagnostic<? extends JavaFileObject> diagnostic : diagnosticCollector.getDiagnostics()) {
			CompilationMessage.Kind kind = (diagnostic.getKind()==Kind.ERROR?CompilationMessage.Kind.ERROR:CompilationMessage.Kind.OTHER);
//			String sourceCode = ((StringBasedJavaSourceFileObject)diagnostic.getSource()).getSourceCode();
			String sourceCode =null;
			try {
				sourceCode = (String)diagnostic.getSource().getCharContent(true);
			}
			catch (IOException ioe) {
				// Unexpected, but leave sourceCode null to indicate it was not retrievable
			}
			catch (NullPointerException npe) {
				// TODO: should we skip warning diagnostics in the loop altogether?
			}
			int startPosition = (int)diagnostic.getPosition();
			if (startPosition == Diagnostic.NOPOS) {
				startPosition = (int)diagnostic.getStartPosition();
			}
			CompilationMessage compilationMessage = new CompilationMessage(kind,diagnostic.getMessage(null),sourceCode,startPosition,(int)diagnostic.getEndPosition());
			compilationResult.recordCompilationMessage(compilationMessage);
		}
		if (success) {
			List<CompiledClassDefinition> ccds = fileManager.getCompiledClasses();
			List<Class<?>> classes = new ArrayList<>();
			try (SimpleClassLoader ccl = new SimpleClassLoader(this.getClass().getClassLoader())) {
				for (CompiledClassDefinition ccd: ccds) {
					Class<?> clazz = ccl.defineClass(ccd.getClassName(), ccd.getBytes());
					classes.add(clazz);
					compilationResult.addClassBytes(ccd.getClassName(), ccd.getBytes());
				}
			} catch (IOException ioe) {
				logger.debug("Unexpected exception defining classes",ioe);
			}
			compilationResult.setCompiledClasses(classes);
		}
		return compilationResult;
	}
}
