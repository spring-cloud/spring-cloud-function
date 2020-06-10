/*
 * Copyright 2012-2019 the original author or authors.
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

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
		assertThat(compilationMessages.isEmpty()).isTrue();
	}

	@Test
	public void missingType() throws Exception {
		Locale.setDefault(Locale.ENGLISH);
		RuntimeJavaCompiler rjc = new RuntimeJavaCompiler();
		CompilationResult cr = rjc.compile("A",
				"public class A implements java.util.function.Supplier { "
						+ "  public String get() {\n"
						+ "    ExpressionParser parser = new SpelExpressionParser();\n"
						+ "    Expression exp = parser.parseExpression(\"'Hello World'\");\n"
						+ "    String message = (String) exp.getValue();"
						+ "    return message;\n" + "  }\n" + "}");
		List<CompilationMessage> compilationMessages = cr.getCompilationMessages();
		assertThat(compilationMessages.size()).isEqualTo(3);
		assertThat(compilationMessages.get(0).getMessage().contains("cannot find symbol"))
				.isTrue();
		assertThat(compilationMessages.get(0).getMessage()
				.contains("class ExpressionParser")).isTrue();
		assertThat(compilationMessages.get(1).getMessage().contains("cannot find symbol"))
				.isTrue();
		assertThat(compilationMessages.get(1).getMessage()
				.contains("class SpelExpressionParser")).isTrue();
		assertThat(compilationMessages.get(2).getMessage().contains("cannot find symbol"))
				.isTrue();
		assertThat(compilationMessages.get(2).getMessage().contains("class Expression"))
				.isTrue();
	}

	@Test
	public void okWithImportedDependencies() throws Exception {
		RuntimeJavaCompiler rjc = new RuntimeJavaCompiler();
		CompilationResult cr = rjc.compile("A",
				"import org.springframework.expression.*;\n"
						+ "import org.springframework.expression.spel.standard.*;\n"
						+ "public class A implements java.util.function.Supplier {\n"
						+ "  public String get() {\n"
						+ "    ExpressionParser parser = new SpelExpressionParser();\n"
						+ "    Expression exp = parser.parseExpression(\"'Hello World'\");\n"
						+ "    String message = (String) exp.getValue();\n"
						+ "    return message;\n" + "  }\n" + "}",
				"maven://org.springframework:spring-expression:4.3.9.RELEASE");
		List<CompilationMessage> compilationMessages = cr.getCompilationMessages();
		assertThat(compilationMessages.isEmpty()).isTrue();
		try (SimpleClassLoader cl = new SimpleClassLoader(
				this.getClass().getClassLoader())) {
			Class<?> clazz = cl.defineClass("A", cr.getClassBytes("A"));
			Supplier<String> supplier = (Supplier<String>) clazz.newInstance();
			assertThat(supplier.get()).isEqualTo("Hello World");
		}
	}

	@Test
	public void okWithImportedDependencies2() throws Exception {
		RuntimeJavaCompiler rjc = new RuntimeJavaCompiler();
		String source = "import org.joda.time.*;\n"
				+ "public class A implements java.util.function.Supplier {\n"
				+ "  public String get() {\n" + "    DateTime dt = new DateTime();\n"
				+ "    int month = dt.getMonthOfYear();\n"
				+ "    return String.valueOf(month>0);\n" + "  }\n" + "}";
		CompilationResult cr = rjc.compile("A", source,
				"maven://joda-time:joda-time:2.9.9");
		List<CompilationMessage> compilationMessages = cr.getCompilationMessages();
		assertThat(compilationMessages.isEmpty()).isTrue();
		List<File> resolvedAdditionalDependencies = cr
				.getResolvedAdditionalDependencies();
		try (SimpleClassLoader cl = new SimpleClassLoader(resolvedAdditionalDependencies,
				this.getClass().getClassLoader())) {
			Class<?> clazz = cl.defineClass("A", cr.getClassBytes("A"));
			Supplier<String> supplier = (Supplier<String>) clazz.newInstance();
			assertThat(supplier.get()).isEqualTo("true");
		}

		cr = rjc.compile("A", source,
				"maven://org.springframework:spring-expression:4.3.9.RELEASE",
				"maven://joda-time:joda-time:2.9.9");
		compilationMessages = cr.getCompilationMessages();
		assertThat(compilationMessages.isEmpty()).isTrue();
		resolvedAdditionalDependencies = cr.getResolvedAdditionalDependencies();
		try (SimpleClassLoader cl = new SimpleClassLoader(resolvedAdditionalDependencies,
				this.getClass().getClassLoader())) {
			Class<?> clazz = cl.defineClass("A", cr.getClassBytes("A"));
			Supplier<String> supplier = (Supplier<String>) clazz.newInstance();
			assertThat(supplier.get()).isEqualTo("true");
		}
	}

	@Test
	public void dependencyResolution() throws Exception {
		// Failure:
		RuntimeJavaCompiler rjc = new RuntimeJavaCompiler();
		CompilationResult cr = rjc.compile("A", "public class A {}",
				"maven://org.springframework:spring-expression2:4.3.9.RELEASE"); // extra
		// '2'
		// in
		// there
		List<CompilationMessage> compilationMessages = cr.getCompilationMessages();
		assertThat(compilationMessages.size()).isEqualTo(1);
		// ERROR:org.eclipse.aether.resolution.ArtifactResolutionException: Could not find
		// artifact org.springframework:spring-expression2:jar:4.3.9.RELEASE in
		// spring-snapshots (https://repo.spring.io/libs-snapshot)
		assertThat(compilationMessages.get(0).getMessage().contains(
				"Could not find artifact org.springframework:spring-expression2:jar:4.3.9.RELEASE"))
						.isTrue();

		// Failure:
		rjc = new RuntimeJavaCompiler();
		cr = rjc.compile("A", "public class A {}",
				"trouble://org.springframework:spring-expression:4.3.9.RELEASE"); // rogue
		// prefix
		// (should
		// be
		// "maven:")
		compilationMessages = cr.getCompilationMessages();
		assertThat(compilationMessages.size()).isEqualTo(1);
		assertThat(compilationMessages.get(0).getMessage()
				.contains("Unrecognized dependency: "))
						.as(compilationMessages.get(0).toString()).isTrue();

		// Success
		rjc = new RuntimeJavaCompiler();
		cr = rjc.compile("A", "public class A {}", "maven://joda-time:joda-time:2.9.9");
		compilationMessages = cr.getCompilationMessages();
		assertThat(compilationMessages.size()).isEqualTo(0);
		List<File> resolvedAdditionalDependencies = cr
				.getResolvedAdditionalDependencies();
		assertThat(resolvedAdditionalDependencies.size()).isEqualTo(1);
		assertThat(resolvedAdditionalDependencies.get(0).toString()
				.endsWith("joda-time-2.9.9.jar"))
						.as("Expected this to end with 'joda-time-2.9.9.jar': "
								+ resolvedAdditionalDependencies.get(0).toString())
						.isTrue();
	}

}
