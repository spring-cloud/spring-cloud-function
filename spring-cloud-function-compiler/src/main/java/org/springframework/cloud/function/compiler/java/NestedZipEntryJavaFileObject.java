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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

/**
 * Represents an element inside in zip which is itself inside a zip. These objects are
 * not initially created with the content of the file they represent,
 * only enough information to find that content because many will
 * typically be created but only few will be opened.
 * 
 * @author Andy Clement
 */
public class NestedZipEntryJavaFileObject implements JavaFileObject {

	private File outerFile;
	private ZipFile outerZipFile;
	private ZipEntry innerZipFile;
	private ZipEntry innerZipFileEntry;

	private URI uri;

	public NestedZipEntryJavaFileObject(File outerFile, ZipFile outerZipFile, ZipEntry innerZipFile, ZipEntry innerZipFileEntry) {
		this.outerFile = outerFile;
		this.outerZipFile = outerZipFile;
		this.innerZipFile = innerZipFile;
		this.innerZipFileEntry = innerZipFileEntry;
	}

	@Override
	public String getName() {
		return innerZipFileEntry.getName(); // Example: a/b/C.class
	}

	@Override
	public URI toUri() {
		if (uri == null) {
			String uriString = null;
			try {
				uriString = "zip:"+outerFile.getAbsolutePath()+"!"+innerZipFile.getName()+"!"+innerZipFileEntry.getName();
				uri = new URI(uriString);
			} catch (URISyntaxException e) {
				throw new IllegalStateException("Unexpected URISyntaxException for string '"+uriString+"'",e);
			}
		}
		return uri;
	}
	
	@Override
	public InputStream openInputStream() throws IOException {
		// Find the inner zip file inside the outer zip file, then
		// find the relevant entry, then return the stream.
		InputStream innerZipFileInputStream = this.outerZipFile.getInputStream(innerZipFile);
		ZipInputStream innerZipInputStream = new ZipInputStream(innerZipFileInputStream);
		ZipEntry nextEntry = innerZipInputStream.getNextEntry();
		while (nextEntry != null) {
			if (nextEntry.getName().equals(innerZipFileEntry.getName())) {
				return innerZipInputStream;
			}
			nextEntry = innerZipInputStream.getNextEntry();
		}
		throw new IllegalStateException("Unable to locate nested zip entry "+innerZipFileEntry.getName()+" in zip "+innerZipFile.getName()+" inside zip "+outerZipFile.getName());
	}

	@Override
	public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
		// It is bytecode
		throw new UnsupportedOperationException("getCharContent() not supported on class file: " + getName());
	}

	@Override
	public long getLastModified() {
		return innerZipFileEntry.getTime();
	}

	@Override
	public Kind getKind() {
		// The filtering before this object was created ensure it is only used for classes
		return Kind.CLASS;
	}

	@Override
	public boolean delete() {
		return false; // Cannot delete entries inside nested zips
	}
	
	@Override
	public OutputStream openOutputStream() throws IOException {
		throw new IllegalStateException("cannot write to nested zip entry: "+toUri());
	}
	
	@Override
	public Writer openWriter() throws IOException {
		throw new IllegalStateException("cannot write to nested zip entry: "+toUri());
	}

	@Override
	public boolean isNameCompatible(String simpleName, Kind kind) {
		if (kind != Kind.CLASS) {
			return false;
		}
		String name = getName();
		int lastSlash = name.lastIndexOf('/');
		return name.substring(lastSlash+1).equals(simpleName+".class");
	}

	@Override
	public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
		// It is bytecode
		throw new UnsupportedOperationException("getCharContent() not supported on class file: " + getName());
	}

	@Override
	public NestingKind getNestingKind() {
		return null; // nesting level not known
	}

	@Override
	public Modifier getAccessLevel() {
		return null; // access level not known
	}
	
	@Override
	public int hashCode() {
		int hc = outerFile.getName().hashCode();
		hc = hc * 37 + innerZipFile.getName().hashCode();
		hc = hc * 37 + innerZipFileEntry.getName().hashCode();
		return hc;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof NestedZipEntryJavaFileObject)) {
			return false;
		}
		NestedZipEntryJavaFileObject that = (NestedZipEntryJavaFileObject)obj;
		return  (outerFile.getName().equals(that.outerFile.getName())) &&
				(innerZipFile.getName().equals(that.innerZipFile.getName())) &&
				(innerZipFileEntry.getName().equals(that.innerZipFileEntry.getName()));
	}
	

}