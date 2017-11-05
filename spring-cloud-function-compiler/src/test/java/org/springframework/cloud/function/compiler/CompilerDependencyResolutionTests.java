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
package org.springframework.cloud.function.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.function.compiler.java.CompilationResult;
import org.springframework.cloud.function.compiler.java.RuntimeJavaCompiler;
import org.springframework.cloud.function.core.FunctionFactoryUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

/**
 * Tests that verify dependency resolution.  Dependencies can be resolved against simple
 * classpath entries or against classes under BOOT-INF/classes or in a nested jar under
 * under BOOT-INF/lib. Finding classes in those locations enables compilation
 * against a packaged boot jar.
 * 
 * @author Andy Clement
 */
public class CompilerDependencyResolutionTests {

	@Test
	public void compilingTestClass() throws Exception {
		ClassDescriptor t1 = compile("Test1","package com.test;\npublic class Test1 { public static String doit() { return \"T1\";}}\n");
		String result = (String) t1.clazz.getDeclaredMethod("doit").invoke(null);
		assertEquals("T1",result);
	}

	@Test
	public void packagingClassesIntoJar() {
		ClassDescriptor t1 = getTestClass("1");
		ClassDescriptor t2 = getTestClass("2");
		File jar = JarBuilder.create().addEntries(t1, t2).getJar();
		assertJarContents(jar, t1, t2);
	}
	
	/**
	 * Doesn't actually verify the caching helps but can be useful to run to see
	 * current numbers.
	 */
	@Test
	public void speedtest() {
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger rootLogger = loggerContext.getLogger("org.springframework.cloud.function.compiler");
		rootLogger.setLevel(Level.ERROR);

		// 10 uses of a single function compiler:
		long stime = System.currentTimeMillis();
		FunctionCompiler<String,String> fc = new FunctionCompiler<String, String>(String.class.getName());
		for (int i=0;i<5;i++) {
			stime = System.currentTimeMillis();
			CompiledFunctionFactory<Function<String, String>> result =
					fc.compile("foos", "flux -> flux.map(v -> v.toUpperCase())", "Flux<String>", "Flux<String>");
			assertThat(FunctionFactoryUtils.isFluxFunction(result.getFactoryMethod())).isTrue();
			System.out.println("Reusing FunctionCompiler: #"+(i+1)+" = "+(System.currentTimeMillis()-stime)+"ms");
		}
		
		// 3 separate FunctionCompilers:
		stime = System.currentTimeMillis();
		CompiledFunctionFactory<Function<String, String>> compiled = new FunctionCompiler<String, String>(
				String.class.getName()).compile("foos", "flux -> flux.map(v -> v.toUpperCase())", "Flux<String>",
						"Flux<String>");
		assertThat(FunctionFactoryUtils.isFluxFunction(compiled.getFactoryMethod())).isTrue();
		long etime = System.currentTimeMillis();
		long time1 = (etime - stime);
		System.out.println("New FunctionCompiler: "+time1+"ms");
		
		stime = System.currentTimeMillis();
		compiled = new FunctionCompiler<String, String>(String.class.getName()).compile("foos",
				"flux -> flux.map(v -> v.toUpperCase())", "Flux<String>", "Flux<String>");
		assertThat(FunctionFactoryUtils.isFluxFunction(compiled.getFactoryMethod())).isTrue();
		etime = System.currentTimeMillis();
		long time2 = (etime - stime);
		System.out.println("New FunctionCompiler: "+time2+"ms");

		stime = System.currentTimeMillis();
		compiled = new FunctionCompiler<String, String>(String.class.getName()).compile("foos",
				"flux -> flux.map(v -> v.toUpperCase())", "Flux<String>", "Flux<String>");
		assertThat(FunctionFactoryUtils.isFluxFunction(compiled.getFactoryMethod())).isTrue();
		etime = System.currentTimeMillis();
		long time3 = (etime - stime);
		System.out.println("New FunctionCompiler: "+time3+"ms");
	}

	@Test
	public void usingJarNoPackageDecl() throws Exception {
		ClassDescriptor tx = compile("TestX","public class TestX { public static String doit() { return \"TX\";}}\n");
		File jar = JarBuilder.create().addEntry(tx).getJar();
		assertJarContents(jar, tx);
		CompilationResult result = new RuntimeJavaCompiler().compile("A",
						"public class A {\n" +
						"  public static Object run() {\n" + 
						"    return new TestX();\n" + 
						"  }\n" + 
						"}",
						jar.toURI().toString());
		assertTrue("Should be no problems: "+result.getCompilationMessages(), result.getCompilationMessages().isEmpty());
		try (URLClassLoader cl = new TestClassLoader(tx,descriptorFromResult(result))) {
			Class<?> class1 = cl.loadClass("A");
			Object invoke = class1.getDeclaredMethod("run").invoke(null);
			assertEquals(tx.name, invoke.getClass().getName());
		}
	}
	

	// A class with no package declaration is placed under BOOT-INF/classes/ in a jar that is then used for resolution
	@Test
	public void usingJarNoPackageDeclBootInfClasses() throws Exception {
		ClassDescriptor t1 = compile("TestX","public class TestX { public static String doit() { return \"TX\";}}\n");
		File jar = JarBuilder.create().addEntryWithPrefix("BOOT-INF/classes/",t1).getJar();
		assertJarContents(jar, "BOOT-INF/classes/", t1);
		CompilationResult result = new RuntimeJavaCompiler().compile("A",
						"public class A {\n" +
						"  public static Object run() {\n" + 
						"    return new TestX();\n" + 
						"  }\n" + 
						"}",
						jar.toURI().toString());
		assertTrue("Should be no problems: "+result.getCompilationMessages(), result.getCompilationMessages().isEmpty());
		try (URLClassLoader cl = new TestClassLoader(t1,descriptorFromResult(result))) {
			Class<?> class1 = cl.loadClass("A");
			Object invoke = class1.getDeclaredMethod("run").invoke(null);
			assertEquals(t1.name, invoke.getClass().getName());
		}
	}
	
	// A class with no package declaration is placed in a jar which is then placed under
	// under BOOT-INF/lib/ in a jar that is then used for resolution
	@Test
	public void usingJarNoPackageDeclNestedBootInfLib() throws Exception {
		ClassDescriptor t1 = compile("TestX","public class TestX { public static String doit() { return \"TX\";}}\n");
		File jar = JarBuilder.create().addEntry(t1).getJar();
		assertJarContents(jar, t1);
		// Now stick that jar in another jar!
		File jar2 = JarBuilder.create().addEntry("BOOT-INF/lib/inner.jar",jar).getJar();
		CompilationResult result = new RuntimeJavaCompiler().compile("A",
						"public class A {\n" +
						"  public static Object run() {\n" + 
						"    return new TestX();\n" + 
						"  }\n" + 
						"}",
						jar2.toURI().toString());
		assertTrue("Should be no problems: "+result.getCompilationMessages(), result.getCompilationMessages().isEmpty());
		try (URLClassLoader cl = new TestClassLoader(t1,descriptorFromResult(result))) {
			Class<?> class1 = cl.loadClass("A");
			Object invoke = class1.getDeclaredMethod("run").invoke(null);
			assertEquals(t1.name, invoke.getClass().getName());
		}
	}
	
	
	// Build a jar containing a type with a package declaration and building against it
	@Test
	public void usingJarWithPackageDecl() throws Exception {
		ClassDescriptor t1 = getTestClass("1");
		File jar = JarBuilder.create().addEntry(t1).getJar();
		assertJarContents(jar, t1);
		CompilationResult result = new RuntimeJavaCompiler().compile("A",
						"import " + t1.name.replace('$', '.') + ";\n" + 
						"public class A {\n" +
						"  public static Object run() {\n" + 
						"    return new Test1();\n" + 
						"  }\n" + 
						"}",
						jar.toURI().toString());
		assertTrue("Should be no problems: "+result.getCompilationMessages(), result.getCompilationMessages().isEmpty());
		try (URLClassLoader cl = new TestClassLoader(t1,descriptorFromResult(result))) {
			Class<?> class1 = cl.loadClass("A");
			Object invoke = class1.getDeclaredMethod("run").invoke(null);
			assertEquals(t1.name, invoke.getClass().getName());
		}
	}

	@Test
	public void usingJarWithPackageDeclBootInfClasses() throws Exception {
		// Here the dependencies are under BOOT-INF/classes in the jar
		ClassDescriptor t1 = getTestClass("1");
		File jar = JarBuilder.create().addEntryWithPrefix("BOOT-INF/classes/",t1).getJar();
		assertJarContents(jar, "BOOT-INF/classes/", t1);
		CompilationResult result = new RuntimeJavaCompiler().compile("A",
						"import " + t1.name.replace('$', '.') + ";\n" + 
						"public class A {\n" +
						"  public static Object run() {\n" + 
						"    return new Test1();\n" + 
						"  }\n" + 
						"}",
						jar.toURI().toString());
		assertTrue("Should be no problems: "+result.getCompilationMessages(), result.getCompilationMessages().isEmpty());
		try (URLClassLoader cl = new TestClassLoader(t1,descriptorFromResult(result))) {
			Class<?> class1 = cl.loadClass("A");
			Object invoke = class1.getDeclaredMethod("run").invoke(null);
			assertEquals(t1.name, invoke.getClass().getName());
		}
	}
	

	@Test
	public void usingJarWithPackageDeclNestedBootInfLib() throws Exception {
		// Here the dependencies are under BOOT-INF/lib/nested.jar
		ClassDescriptor t1 = getTestClass("1");
		File jar = JarBuilder.create().addEntry(t1).getJar();
		assertJarContents(jar, t1);
		// Now stick that jar in another jar!
		File jar2 = JarBuilder.create().addEntry("BOOT-INF/lib/inner.jar",jar).getJar();
		CompilationResult result = new RuntimeJavaCompiler().compile("A",
						"import " + t1.name.replace('$', '.') + ";\n" + 
						"public class A {\n" +
						"  public static Object run() {\n" + 
						"    return new Test1();\n" + 
						"  }\n" + 
						"}",
						jar2.toURI().toString());
		assertTrue("Should be no problems: "+result.getCompilationMessages(), result.getCompilationMessages().isEmpty());
		try (URLClassLoader cl = new TestClassLoader(t1,descriptorFromResult(result))) {
			Class<?> class1 = cl.loadClass("A");
			Object invoke = class1.getDeclaredMethod("run").invoke(null);
			assertEquals(t1.name, invoke.getClass().getName());
		}
	}

	// ---
	
	// Simple classloader that can load from descriptors
	class TestClassLoader extends URLClassLoader {

		ClassDescriptor[] descriptors;
		
		public TestClassLoader(ClassDescriptor... descriptors) {
			super(new URL[0], TestClassLoader.class.getClassLoader());
			this.descriptors = descriptors;
		}

		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException {
			for (ClassDescriptor descriptor: descriptors) {
				if (descriptor.name.equals(name)) {
					return defineClass(descriptor.name, descriptor.bytes, 0, descriptor.bytes.length);
				}
			}
			return super.findClass(name);
		}
		
	}

	// Simple holder for the result of compilation
	static class ClassDescriptor {
		final String name;
		final byte[] bytes;
		final Class<?> clazz;
		
		public ClassDescriptor(String name, byte[] bytes, Class<?> clazz) {
			this.name = name;
			this.bytes = bytes;
			this.clazz = clazz;
		}
	}
	
	private ClassDescriptor descriptorFromResult(CompilationResult result) {
		Class<?> clazz = result.getCompiledClasses().get(0);
		return new ClassDescriptor(clazz.getName(),result.getClassBytes(clazz.getName()),clazz);
	}

	private ClassDescriptor compile(String className, String classSourceCode) {
		CompilationResult compile = new RuntimeJavaCompiler().compile(className, classSourceCode);
		assertTrue("Should be empty: \n"+compile.getCompilationMessages(), compile.getCompilationMessages().isEmpty());
		Class<?> clazz = compile.getCompiledClasses().get(0);
		return new ClassDescriptor(clazz.getName(),compile.getClassBytes(clazz.getName()), compile.getCompiledClasses().get(0));
	}
	
	private ClassDescriptor getTestClass(String suffix) {
		try {
			return compile("Test"+suffix,"package com.test;\npublic class Test"+suffix+" { public static String doit() { return \"T"+suffix+"\";}}\n");
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
	
	private void assertJarContents(File jar, ClassDescriptor... classdescriptors) {
		assertJarContents(jar, "", classdescriptors);
	}

	private void assertJarContents(File jar, String prefix, ClassDescriptor... classDescriptors) {
		List<String> clazzes = new ArrayList<>();
		for (ClassDescriptor classDescriptor : classDescriptors) {
			clazzes.add(prefix + classDescriptor.name.replace('.', '/') + ".class");
		}
		walkJar(jar, (entry) -> clazzes.remove(entry.getName()));
		assertTrue("Should be empty: " + clazzes, clazzes.isEmpty());
	}

	private void walkJar(File jar, Consumer<JarEntry> fn) {
		try {
			JarInputStream jarInputStream = new JarInputStream(new FileInputStream(jar));
			while (true) {
				JarEntry nextJarEntry = jarInputStream.getNextJarEntry();
				if (nextJarEntry == null) {
					break;
				}
				fn.accept(nextJarEntry);
			}
			jarInputStream.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	@SuppressWarnings("unused")
	private void printJar(File jar) {
		System.out.println("Contents of jar: " + jar);
		walkJar(jar, (entry) -> {
			System.out.println("- " + entry.getName());
		});
	}

	static class JarBuilder {

		File jarFile;
		JarOutputStream jos;

		private JarBuilder() {
			try {
				File newJar = File.createTempFile("test", ".jar");
				jarFile = newJar.getAbsoluteFile();
				newJar.delete();
				jos = new JarOutputStream(new FileOutputStream(jarFile));
				jarFile.deleteOnExit();
			} catch (IOException e) {
				throw new IllegalStateException("Unexpected problem creating file", e);
			}
		}

		public JarBuilder addEntry(String entryName, File entryContentFile) {
			try {
				ZipEntry ze = new ZipEntry(entryName);
				jos.putNextEntry(ze);
				jos.write(loadBytes(entryContentFile));
				jos.closeEntry();
				return this;
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}

		private byte[] loadBytes(File f) {
			try (InputStream is = new FileInputStream(f)) {
				byte[] bs = null;
				byte[] buf = new byte[10000];
				int readCount;
				while ((readCount = is.read(buf)) != -1) {
					if (bs == null) {
						bs = new byte[readCount];
						System.arraycopy(buf, 0, bs, 0, readCount);
					} else {
						byte[] newbs = new byte[bs.length + readCount];
						System.arraycopy(bs, 0, newbs, 0, bs.length);
						System.arraycopy(buf, 0, newbs, bs.length, readCount);
						bs = newbs;
					}
				}
				return bs;
			} catch (IOException ioe) {
				throw new IllegalStateException(ioe);
			}
		}

		public JarBuilder addEntries(ClassDescriptor... classes) {
			for (ClassDescriptor clazz : classes) {
				addEntry(clazz);
			}
			return this;
		}

		public JarBuilder addEntry(ClassDescriptor clazz) {
			return addEntryWithPrefix("", clazz);
		}

		public JarBuilder addEntryWithPrefix(String prefix, ClassDescriptor holder) {
			try {
				String n = holder.name.replace('.', '/') + ".class";
				ZipEntry ze = new ZipEntry(prefix + n);
				jos.putNextEntry(ze);
				jos.write(holder.bytes);
				jos.closeEntry();
				return this;
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}

		public static JarBuilder create() {
			return new JarBuilder();
		}

		private File getJar() {
			try {
				jos.close();
			} catch (IOException e) {
				throw new IllegalStateException("Unable to close jar", e);
			}
			return jarFile;
		}

	}

}
