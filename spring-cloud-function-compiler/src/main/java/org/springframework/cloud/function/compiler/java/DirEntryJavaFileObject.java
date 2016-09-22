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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

/**
 * A JavaFileObject that represents a file in a directory.
 * 
 * @author Andy Clement
 */
public class DirEntryJavaFileObject implements JavaFileObject {

	private File file;
	private File basedir;

	public DirEntryJavaFileObject(File basedir, File file) {
		this.basedir = basedir;
		this.file = file;
	}

	@Override
	public URI toUri() {
		return file.toURI();
	}

	/**
	 * @return the path of the file relative to the base directory, for example: a/b/c/D.class
	 */
	@Override
	public String getName() {
		String basedirPath = basedir.getPath();
		String filePath = file.getPath();
		return filePath.substring(basedirPath.length()+1);
	}

	@Override
	public InputStream openInputStream() throws IOException {
		return new FileInputStream(file);
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
		return file.lastModified();
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
		return file.getName().hashCode()*37+basedir.getName().hashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof DirEntryJavaFileObject)) {
			return false;
		}
		DirEntryJavaFileObject that = (DirEntryJavaFileObject)obj;
		return (basedir.getName().equals(that.basedir.getName())) && (file.getName().equals(that.file.getName()));
	}
	
}