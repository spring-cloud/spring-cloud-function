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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Walks a JrtFS treating it like a directory (to avoid overcomplicating the walking
 * logic in IterableClasspath)
 * 
 * @author Andy Clement
 */
public class JrtFsEnumeration implements Enumeration<JrtEntryJavaFileObject> {
	
//	private final static Logger logger = LoggerFactory.getLogger(JrtFsEnumeration.class);

	private static URI JRT_URI = URI.create("jrt:/"); //$NON-NLS-1$
	
	private final static FileSystem fs = FileSystems.getFileSystem(JRT_URI);
	
	private Path pathWithinJrt;
	
	private List<JrtEntryJavaFileObject> jfos = new ArrayList<>();
	
	private Integer counter = 0;
	
	private Boolean initialized = false;

	public JrtFsEnumeration(File jrtFsFile, Path pathWithinJrt) {
		this.pathWithinJrt = pathWithinJrt;
		ensureInitialized();
	}

	class FileCacheBuilderVisitor extends SimpleFileVisitor<Path> {
		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			int fnc = file.getNameCount();
			if (fnc >= 3 && file.toString().endsWith(".class")) { // There is a preceeding module name - e.g. /modules/java.base/java/lang/Object.class
				// file.subpath(2, fnc); // e.g. java/lang/Object.class
				jfos.add(new JrtEntryJavaFileObject(file));
			}
			return FileVisitResult.CONTINUE;
		}
	}
	
	private void ensureInitialized() {
		synchronized (initialized) {
			if (initialized) {
				return;
			}
			FileCacheBuilderVisitor visitor = new FileCacheBuilderVisitor();
			if (pathWithinJrt != null) {
				try {
					Files.walkFileTree(pathWithinJrt, visitor);
					// System.out.println("JrtFs enumeration for '"+pathWithinJrt+"' with #"+jfos.size()+" entries");
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			} else {
				Iterable<java.nio.file.Path> roots = fs.getRootDirectories();
				try {
					for (java.nio.file.Path path : roots) {
						Files.walkFileTree(path, visitor);
					}
					// System.out.println("JrtFs enumeration initialized with #"+jfos.size()+" entries");
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
			initialized = true;
		}
	}

	@Override
	public boolean hasMoreElements() {
		return counter < jfos.size();
	}

	@Override
	public JrtEntryJavaFileObject nextElement() {
		if (counter>=jfos.size()) {
			throw new NoSuchElementException();
		}
		JrtEntryJavaFileObject toReturn = jfos.get(counter++);
		return toReturn;
	}

	/**
	 * Return the relative path of this file to the base directory that the directory enumeration was
	 * started for.
	 * @param file a file discovered returned by this enumeration 
	 * @return the relative path of the file (for example: a/b/c/D.class)
	 */
	public String getName(JrtEntryJavaFileObject file) {
		return file.getPathToClassString();
	}
	
	public void reset() {
		counter = 0;
	}

}
