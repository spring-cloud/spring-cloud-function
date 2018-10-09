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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

/**
 * A JavaFileObject that represents a class from the Java runtime as packaged in Java 9 and later.
 * 
 * @author Andy Clement
 */
public class JrtEntryJavaFileObject implements JavaFileObject {

	private String pathToClassString;
	private Path path;

	/**
	 * @param path entry in the Java runtime filesystem, for example '/modules/java.base/java/lang/Object.class'
	 */
	public JrtEntryJavaFileObject(Path path) {
		this.pathToClassString = path.subpath(2, path.getNameCount()).toString(); // e.g. java/lang/Object.class
		this.path = path;
	}

	@Override
	public URI toUri() {
		return path.toUri();
	}

	/**
	 * @return the path of the file relative to the base directory, for example: a/b/c/D.class
	 */
	@Override
	public String getName() {
		return pathToClassString;
	}

	@Override
	public InputStream openInputStream() throws IOException {
		byte[] bytes = Files.readAllBytes(path);
		return new ByteArrayInputStream(bytes);
	}

	@Override
	public OutputStream openOutputStream() throws IOException {
		throw new IllegalStateException("Only expected to be used for input");
	}

	@Override
	public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
		// It is bytecode
		throw new UnsupportedOperationException("openReader() not supported on class file: " + getName());
	}

	@Override
	public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
		// It is bytecode
		throw new UnsupportedOperationException("getCharContent() not supported on class file: " + getName());
	}

	@Override
	public Writer openWriter() throws IOException {
		throw new IllegalStateException("only expected to be used for input");
	}

	@Override
	public long getLastModified() {
		try {
			return Files.getLastModifiedTime(path).toMillis();
		} catch (IOException ioe) {
			throw new RuntimeException("Unable to determine last modified time of "+pathToClassString, ioe);
		}
	}

	@Override
	public boolean delete() {
		return false; // This object is for read only access to a class
	}

	@Override
	public Kind getKind() {
		return Kind.CLASS;
	}

	@Override
	public boolean isNameCompatible(String simpleName, Kind kind) {
		if (kind != Kind.CLASS) {
			return false;
		}
		String name = getName();
		int lastSlash = name.lastIndexOf('/');
		return name.substring(lastSlash + 1).equals(simpleName + ".class");
	}

	@Override
	public NestingKind getNestingKind() {
		return null;
	}

	@Override
	public Modifier getAccessLevel() {
		return null;
	}

	@Override
	public int hashCode() {
		return getName().hashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof JrtEntryJavaFileObject)) {
			return false;
		}
		JrtEntryJavaFileObject that = (JrtEntryJavaFileObject)obj;
		return (getName().equals(that.getName()));
	}

	public String getPathToClassString() {
		return pathToClassString;
	}
	
}