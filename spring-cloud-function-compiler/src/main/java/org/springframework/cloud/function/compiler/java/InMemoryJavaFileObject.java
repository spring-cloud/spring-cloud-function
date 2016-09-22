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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.FileObject;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JavaFileObject that represents a source artifact created for compilation or an output
 * artifact producing during compilation (a .class file or some other thing if an annotation
 * processor has run). In order to be clear what it is being used for there are static factory
 * methods that ask for specific types of file.
 * 
 * @author Andy Clement
 */
public class InMemoryJavaFileObject implements JavaFileObject {

	private final static Logger logger = LoggerFactory.getLogger(InMemoryJavaFileObject.class);
	
	private Location location;
	private String packageName;
	private String relativeName;
	private FileObject sibling;
	private String className;
	private Kind kind;
	
	private byte[] content = null;
	private long lastModifiedTime = 0;
	private URI uri = null;
	
	private InMemoryJavaFileObject() {}
	
	public static InMemoryJavaFileObject getFileObject(Location location, String packageName, String relativeName, FileObject sibling) {
		InMemoryJavaFileObject retval = new InMemoryJavaFileObject();
		retval.kind = Kind.OTHER;
		retval.location = location;
		retval.packageName = packageName;
		retval.relativeName = relativeName;
		retval.sibling = sibling;
		return retval;
	}
	
	public static InMemoryJavaFileObject getJavaFileObject(Location location, String className, Kind kind, FileObject sibling) {
		InMemoryJavaFileObject retval = new InMemoryJavaFileObject();
		retval.location = location;
		retval.className = className;
		retval.kind = kind;
		retval.sibling = sibling;
		return retval;
	}

	public static InMemoryJavaFileObject getSourceJavaFileObject(String className, String content) {
		InMemoryJavaFileObject retval = new InMemoryJavaFileObject();
		retval.location = StandardLocation.SOURCE_PATH;
		retval.className = className;
		retval.kind = Kind.SOURCE;
		retval.content = content.getBytes();
		return retval;
	}
	
	public byte[] getBytes() {
		return content;
	}

	public String toString() {
		return "OutputJavaFileObject: Location="+location+",className="+className+",kind="+kind+",relativeName="+relativeName+",sibling="+sibling+",packageName="+packageName;
	}
	
	@Override
	public URI toUri() {
		// These memory based output files 'pretend' to be relative to the file system root
		if (uri == null) {
			String name = null;
			if (className != null) {
				name = className.replace('.', '/');
			} else if (packageName !=null && packageName.length()!=0) {
				name = packageName.replace('.', '/')+'/'+relativeName;
			} else {
				name = relativeName;
			}
			
			String uriString = null;
			try {
				uriString = "file:/"+name+kind.extension;
				uri = new URI(uriString);
			} catch (URISyntaxException e) {
				throw new IllegalStateException("Unexpected URISyntaxException for string '" + uriString + "'", e);
			}
		}
		return uri;
	}

	@Override
	public String getName() {
		return toUri().getPath();
	}

	@Override
	public InputStream openInputStream() throws IOException {
		if (content == null) {
			throw new FileNotFoundException();
		}
		logger.debug("opening input stream for {}",getName());
		return new ByteArrayInputStream(content);
	}

	@Override
	public OutputStream openOutputStream() throws IOException {
		logger.debug("opening output stream for {}",getName());
		return new ByteArrayOutputStream() {
			@Override
			public void close() throws IOException {
				super.close();
				lastModifiedTime = System.currentTimeMillis();
				content = this.toByteArray();
			}
		};
	}

	@Override
	public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
		return new InputStreamReader(openInputStream(), Charset.defaultCharset());
	}

	@Override
	public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
		if (kind!=Kind.SOURCE) {
			throw new UnsupportedOperationException("getCharContent() not supported on file object: " + getName());
		}
		// Not yet supporting encodings
		return (content==null?null:new String(content));
	}

	@Override
	public Writer openWriter() throws IOException {
		// Let's not enforce this restriction right now
//		if (kind == Kind.CLASS) {
//			throw new UnsupportedOperationException("openWriter() not supported on file object: " + getName());
//		}
		return new CharArrayWriter() {
			@Override
			public void close() {
				lastModifiedTime = System.currentTimeMillis();
				content = new String(toCharArray()).getBytes(); // Ignoring encoding...
			};
		};
	}

	@Override
	public long getLastModified() {
		return lastModifiedTime;
	}

	@Override
	public boolean delete() {
		return false;
	}

	@Override
	public Kind getKind() {
		return kind;
	}

	public boolean isNameCompatible(String simpleName, Kind kind) {
        String baseName = simpleName + kind.extension;
        return kind.equals(getKind())
            && (baseName.equals(toUri().getPath())
                || toUri().getPath().endsWith("/" + baseName));
    }

	@Override
	public NestingKind getNestingKind() {
		return null;
	}

	@Override
	public Modifier getAccessLevel() {
		return null;
	}
	
}