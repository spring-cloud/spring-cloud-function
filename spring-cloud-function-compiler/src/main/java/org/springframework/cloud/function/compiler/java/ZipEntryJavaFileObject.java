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

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

public class ZipEntryJavaFileObject implements JavaFileObject {

	private File containingFile;
	private ZipFile zf;
	private ZipEntry ze;

	private URI uri;

	public ZipEntryJavaFileObject(File containingFile, ZipFile zipFile, ZipEntry entry) {
		this.containingFile = containingFile;
		this.zf = zipFile;
		this.ze = entry;
	}

	@Override
	public URI toUri() {
		if (uri == null) {
			String uriString = null;
			try {
				uriString = "zip:" + containingFile.getAbsolutePath() + "!" + ze.getName();
				uri = new URI(uriString);
			} catch (URISyntaxException e) {
				throw new IllegalStateException("Unexpected URISyntaxException for string '" + uriString + "'", e);
			}
		}
		return uri;
	}

	@Override
	public String getName() {
		return ze.getName(); // a/b/C.class
	}

	@Override
	public InputStream openInputStream() throws IOException {
		return zf.getInputStream(ze);
	}

	@Override
	public OutputStream openOutputStream() throws IOException {
		throw new IllegalStateException("only expected to be used for input");
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
		return ze.getTime();
	}

	@Override
	public boolean delete() {
		return false; // Cannot delete entries inside zips
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
		int hc = containingFile.getName().hashCode();
		hc = hc * 37 + ze.getName().hashCode();
		return hc;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof ZipEntryJavaFileObject)) {
			return false;
		}
		ZipEntryJavaFileObject that = (ZipEntryJavaFileObject)obj;
		return  (containingFile.getName().equals(that.containingFile.getName())) &&
				(ze.getName().equals(that.ze.getName()));
	}

}