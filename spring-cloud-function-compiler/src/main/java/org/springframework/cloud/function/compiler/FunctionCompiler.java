/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.compiler;

import java.util.List;
import java.util.regex.Matcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.function.compiler.java.CompilationFailedException;
import org.springframework.cloud.function.compiler.java.CompilationMessage;
import org.springframework.cloud.function.compiler.java.CompilationResult;
import org.springframework.cloud.function.compiler.java.RuntimeJavaCompiler;

/**
 * @author Andy Clement
 * @author Mark Fisher
 */
public class FunctionCompiler {

	private final static String PACKAGE = "org.springframework.cloud.function.compiler";

	public final static String GENERATED_FUNCTION_FACTORY_CLASS_NAME = PACKAGE + ".GeneratedFunctionFactory";

	private static Logger logger = LoggerFactory.getLogger(FunctionCompiler.class);

	// Newlines in the property are escaped
	private static final String NEWLINE_ESCAPE = Matcher.quoteReplacement("\\n");

	// Individual double-quote characters are represented by two double quotes in the DSL
	private static final String DOUBLE_DOUBLE_QUOTE = Matcher.quoteReplacement("\"\"");

	/**
	 * The user supplied code snippet is inserted into the template and then the result is compiled
	 */
	private static String SOURCE_CODE_TEMPLATE =
			"package " + PACKAGE + ";\n" +
			"import java.util.*;\n" + // Helpful to include this
			"import java.util.function.*;\n" +
			"import reactor.core.publisher.Flux;\n" +
			"public class GeneratedFunctionFactory implements FunctionFactory {\n" +
			" public Function<Flux<Object>, Flux<Object>> getFunction() {\n" +
			"  %s\n" +
			" }\n" +
			"}\n";

	private final RuntimeJavaCompiler compiler = new RuntimeJavaCompiler();

	/**
	 * Produce a CompiledFunctionFactory instance by:<ul>
	 * <li>Decoding the code String to process any newlines/double-double-quotes
	 * <li>Insert the code into the source code template for a class
	 * <li>Compiling the class using the JDK provided Java Compiler
	 * <li>Loading the compiled class
	 * <li>Invoking a well known method on the factory class to produce a Function instance
	 * <li>Returning that instance.
	 * </ul>
	 *
	 * @return a CompiledFunctionFactory instance
	 */
	public <T, R> CompiledFunctionFactory<T, R> compile(String code) {
		logger.info("Initial code property value :'{}'", code);
 		code = decode(code);
 		if (code.startsWith("\"") && code.endsWith("\"")) {
 			code = code.substring(1,code.length()-1);
 		}
 		if (!code.startsWith("return ") && !code.endsWith(";")) {
 			code = "return (Function<Flux<Object>,Flux<Object>> & java.io.Serializable) " + code + ";";
 		}
		logger.info("Processed code property value :\n{}\n", code);
		CompilationResult compilationResult = buildAndCompileSourceCode(code);
		if (compilationResult.wasSuccessful()) {
			return new CompiledFunctionFactory<>(compilationResult);
		}
		List<CompilationMessage> compilationMessages = compilationResult.getCompilationMessages();
		throw new CompilationFailedException(compilationMessages);
	}

	/**
	 * Create the source for and then compile and load a class that embodies
	 * the supplied methodBody. The methodBody is inserted into a class template that
	 * returns a <tt>Function&lt;Flux&lt;Object&gt;,Flux&lt;Object&gt;&gt;</tt>.
	 * This method can return more than one class if the method body includes local class
	 * declarations. An example methodBody would be <tt>return input -> input.buffer(5).map(list->list.get(0));</tt>.
	 *
	 * @param methodBody the source code for a method that should return a
	 * <tt>Function&lt;Flux&lt;Object&gt;,Flux&lt;Object&gt;&gt;</tt>
	 * @return the list of Classes produced by compiling and then loading the snippet of code
	 */
	private CompilationResult buildAndCompileSourceCode(String methodBody) {
		String sourceCode = makeSourceClassDefinition(methodBody);
		return compiler.compile(GENERATED_FUNCTION_FACTORY_CLASS_NAME, sourceCode);
	}

	private static String decode(String input) {
		return input.replaceAll(NEWLINE_ESCAPE, "\n").replaceAll(DOUBLE_DOUBLE_QUOTE, "\"");
	}

	/**
	 * Make a full source code definition for a class by applying the specified method body
	 * to the Reactive template.
	 *
	 * @param methodBody the code to insert into the Reactive source class template
	 * @return a complete Java Class definition
	 */
	private static String makeSourceClassDefinition(String methodBody) {
		return String.format(SOURCE_CODE_TEMPLATE, methodBody);
	}

}
