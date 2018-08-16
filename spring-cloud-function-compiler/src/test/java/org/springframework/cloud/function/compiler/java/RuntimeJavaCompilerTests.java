/*
 * Copyright 2017 the original author or authors.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import org.junit.Test;

/**
 * @author Andy Clement
 */
@SuppressWarnings("unchecked")
public class RuntimeJavaCompilerTests {

	@Test
	public void basicCompilation() {
		RuntimeJavaCompiler rjc = new RuntimeJavaCompiler();
		CompilationResult cr = rjc.compile("A", "public class A {}");
		List<CompilationMessage> compilationMessages = cr.getCompilationMessages();
		assertTrue(compilationMessages.isEmpty());
	}
	
	@Test
	public void missingType() throws Exception {
		Locale.setDefault(Locale.ENGLISH);
		RuntimeJavaCompiler rjc = new RuntimeJavaCompiler();
		CompilationResult cr = rjc.compile("A", 
				"public class A implements java.util.function.Supplier { "+
				"  public String get() {\n"+
				"    ExpressionParser parser = new SpelExpressionParser();\n" + 
				"    Expression exp = parser.parseExpression(\"'Hello World'\");\n" + 
				"    String message = (String) exp.getValue();"+
				"    return message;\n"+
				"  }\n"+
				"}");
		List<CompilationMessage> compilationMessages = cr.getCompilationMessages();
		assertEquals(3,compilationMessages.size());
		assertTrue(compilationMessages.get(0).getMessage().contains("cannot find symbol"));
		assertTrue(compilationMessages.get(0).getMessage().contains("class ExpressionParser"));
		assertTrue(compilationMessages.get(1).getMessage().contains("cannot find symbol"));
		assertTrue(compilationMessages.get(1).getMessage().contains("class SpelExpressionParser"));
		assertTrue(compilationMessages.get(2).getMessage().contains("cannot find symbol"));
		assertTrue(compilationMessages.get(2).getMessage().contains("class Expression"));
	}

	@Test
	public void okWithImportedDependencies() throws Exception {
		RuntimeJavaCompiler rjc = new RuntimeJavaCompiler();
		CompilationResult cr = rjc.compile("A",
			"import org.springframework.expression.*;\n"+
			"import org.springframework.expression.spel.standard.*;\n"+
			"public class A implements java.util.function.Supplier {\n"+
			"  public String get() {\n"+
			"    ExpressionParser parser = new SpelExpressionParser();\n" + 
			"    Expression exp = parser.parseExpression(\"'Hello World'\");\n" + 
			"    String message = (String) exp.getValue();\n"+
			"    return message;\n"+
			"  }\n"+
			"}","maven://org.springframework:spring-expression:4.3.9.RELEASE");
		List<CompilationMessage> compilationMessages = cr.getCompilationMessages();
		assertTrue(compilationMessages.isEmpty());
		try (SimpleClassLoader cl = new SimpleClassLoader(this.getClass().getClassLoader())) {
			Class<?> clazz = cl.defineClass("A",cr.getClassBytes("A"));
			Supplier<String> supplier = (Supplier<String>) clazz.newInstance();
			assertEquals("Hello World",supplier.get());
		}
	}

	@Test
	public void okWithImportedDependencies2() throws Exception {
		RuntimeJavaCompiler rjc = new RuntimeJavaCompiler();
		String source = 
				"import org.joda.time.*;\n"+
				"public class A implements java.util.function.Supplier {\n"+
				"  public String get() {\n"+
				"    DateTime dt = new DateTime();\n" + 
				"    int month = dt.getMonthOfYear();\n"+
				"    return String.valueOf(month>0);\n"+
				"  }\n"+
				"}";
		CompilationResult cr = rjc.compile("A", source, "maven://joda-time:joda-time:2.9.9");
		List<CompilationMessage> compilationMessages = cr.getCompilationMessages();
		assertTrue(compilationMessages.isEmpty());
		List<File> resolvedAdditionalDependencies = cr.getResolvedAdditionalDependencies();
		try (SimpleClassLoader cl = new SimpleClassLoader(resolvedAdditionalDependencies, this.getClass().getClassLoader())) {
			Class<?> clazz = cl.defineClass("A",cr.getClassBytes("A"));
			Supplier<String> supplier = (Supplier<String>) clazz.newInstance();
			assertEquals("true",supplier.get());
		}
		
		cr = rjc.compile("A", source,
				"maven://org.springframework:spring-expression:4.3.9.RELEASE",
				"maven://joda-time:joda-time:2.9.9");
		compilationMessages = cr.getCompilationMessages();
		assertTrue(compilationMessages.isEmpty());
		resolvedAdditionalDependencies = cr.getResolvedAdditionalDependencies();
		try (SimpleClassLoader cl = new SimpleClassLoader(resolvedAdditionalDependencies, this.getClass().getClassLoader())) {
			Class<?> clazz = cl.defineClass("A",cr.getClassBytes("A"));
			Supplier<String> supplier = (Supplier<String>) clazz.newInstance();
			assertEquals("true",supplier.get());
		}
	}

	@Test
	public void dependencyResolution() throws Exception {
		// Failure:
		RuntimeJavaCompiler rjc = new RuntimeJavaCompiler();
		CompilationResult cr = rjc.compile("A",
			"public class A {}",
			"maven://org.springframework:spring-expression2:4.3.9.RELEASE"); // extra '2' in there
		List<CompilationMessage> compilationMessages = cr.getCompilationMessages();
		assertEquals(1,compilationMessages.size());
		// ERROR:org.eclipse.aether.resolution.ArtifactResolutionException: Could not find artifact org.springframework:spring-expression2:jar:4.3.9.RELEASE in spring-snapshots (https://repo.spring.io/libs-snapshot)
		assertTrue(compilationMessages.get(0).getMessage().contains("Could not find artifact org.springframework:spring-expression2:jar:4.3.9.RELEASE"));

		// Failure:
		rjc = new RuntimeJavaCompiler();
		cr = rjc.compile("A",
			"public class A {}",
			"trouble://org.springframework:spring-expression:4.3.9.RELEASE"); // rogue prefix (should be "maven:")
		compilationMessages = cr.getCompilationMessages();
		assertEquals(1,compilationMessages.size());
		assertTrue(compilationMessages.get(0).toString(),compilationMessages.get(0).getMessage().contains("Unrecognized dependency: "));

		// Success
		rjc = new RuntimeJavaCompiler();
		cr = rjc.compile("A",
			"public class A {}",
			"maven://joda-time:joda-time:2.9.9");
		compilationMessages = cr.getCompilationMessages();
		assertEquals(0,compilationMessages.size());
		List<File> resolvedAdditionalDependencies = cr.getResolvedAdditionalDependencies();
		assertEquals(1, resolvedAdditionalDependencies.size());
		assertTrue("Expected this to end with 'joda-time-2.9.9.jar': "+resolvedAdditionalDependencies.get(0).toString(),
				resolvedAdditionalDependencies.get(0).toString().endsWith("joda-time-2.9.9.jar"));
	}

}
