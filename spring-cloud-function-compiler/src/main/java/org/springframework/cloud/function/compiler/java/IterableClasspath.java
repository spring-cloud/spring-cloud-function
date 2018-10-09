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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import javax.tools.JavaFileObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.function.compiler.java.MemoryBasedJavaFileManager.CompilationInfoCache;
import org.springframework.cloud.function.compiler.java.MemoryBasedJavaFileManager.CompilationInfoCache.ArchiveInfo;

/**
 * Iterable that will produce an iterator that returns classes found
 * on a specified classpath that meet specified criteria. For jars it finds, the
 * iterator will go into nested jars - this handles the situation with a
 * spring boot uberjar.
 *
 * @author Andy Clement
 */
public class IterableClasspath extends CloseableFilterableJavaFileObjectIterable {

	private static Logger logger = LoggerFactory.getLogger(IterableClasspath.class);

	private List<File> classpathEntries = new ArrayList<>();

	private List<ZipFile> openArchives = new ArrayList<>();

	/**
	 * @param compilationInfoCache cache of info that may help accelerate compilation
	 * @param classpath a classpath of jars/directories
	 * @param packageNameFilter an optional package name if choosing to filter (e.g. com.example)
	 * @param includeSubpackages if true, include results in subpackages of the specified package filter
	 */
	IterableClasspath(CompilationInfoCache compilationInfoCache, String classpath, String packageNameFilter, boolean includeSubpackages) {
		super(compilationInfoCache, packageNameFilter, includeSubpackages);
		StringTokenizer tokenizer = new StringTokenizer(classpath, File.pathSeparator);
		while (tokenizer.hasMoreElements()) {
			String nextEntry = tokenizer.nextToken();
			File f = new File(nextEntry);
			if (f.exists()) {
				// Skip iterating over archives that cannot possibly match the filter
				if (this.packageNameFilter != null && this.packageNameFilter.length() > 0) {
					ArchiveInfo archiveInfo = compilationInfoCache.getArchiveInfoFor(f);
					if (archiveInfo != null && !archiveInfo.containsPackage(this.packageNameFilter, this.includeSubpackages)) {
						continue;
					}
				}
				classpathEntries.add(f);
			} else {
				logger.debug("path element does not exist {}",f);
			}
		}
	}

	public void close() {
		for (ZipFile openArchive : openArchives) {
			try {
				openArchive.close();
			} catch (IOException ioe) {
				logger.debug("Unexpected error closing archive {}",openArchive,ioe);
			}
		}
		openArchives.clear();
	}

	public Iterator<JavaFileObject> iterator() {
		return new ClasspathEntriesIterator();
	}

	class ClasspathEntriesIterator implements Iterator<JavaFileObject> {
		private int currentClasspathEntriesIndex = 0;

		// Walking one of three possible things: directory tree, zip, or Java runtime packaged in JDK9+ form
		private File openDirectory = null;
		private DirEnumeration openDirectoryEnumeration = null;

		private ZipFile openArchive = null;
		private File openFile = null;
		private ZipEntry nestedZip = null;
		private Stack<Enumeration<? extends ZipEntry>> openArchiveEnumeration = null;
		
		private File openJrt;
		private JrtFsEnumeration openJrtEnumeration = null;

		private JavaFileObject nextEntry = null;

		private void findNext() {
			if (nextEntry == null) {
				try {
					while (openArchive!=null || openDirectory!=null || openJrt != null || currentClasspathEntriesIndex < classpathEntries.size()) {
						if (openArchive == null && openDirectory == null && openJrt == null) {
							// Open the next item
							File nextFile = classpathEntries.get(currentClasspathEntriesIndex);
							if (nextFile.isDirectory()) {
								openDirectory = nextFile;
								openDirectoryEnumeration = new DirEnumeration(nextFile);
							} else if (nextFile.getName().endsWith("jrt-fs.jar")) {
								openJrt = nextFile;
								openJrtEnumeration = new JrtFsEnumeration(nextFile,null);
							} else {
								openFile = nextFile;
								openArchive = new ZipFile(nextFile);
								openArchives.add(openArchive);
								openArchiveEnumeration = new Stack<Enumeration<? extends ZipEntry>>();
								openArchiveEnumeration.push(openArchive.entries());
							}
							currentClasspathEntriesIndex++;
						}
						if (openArchiveEnumeration != null) {
							while (!openArchiveEnumeration.isEmpty()) {
								while (openArchiveEnumeration.peek().hasMoreElements()) {
									ZipEntry entry = openArchiveEnumeration.peek().nextElement();
									String entryName = entry.getName();
									if (accept(entryName)) {
										if (nestedZip!=null) {
											nextEntry = new NestedZipEntryJavaFileObject(openFile, openArchive,nestedZip, entry);
										} else {
											nextEntry = new ZipEntryJavaFileObject(openFile, openArchive, entry);
										}
										return;
									} else if (nestedZip == null && entryName.startsWith(MemoryBasedJavaFileManager.BOOT_PACKAGING_PREFIX_FOR_LIBRARIES) && entryName.endsWith(".jar")) {
										// nested jar in uber jar
										logger.debug("opening nested archive {}",entry.getName());
										ZipInputStream zis = new ZipInputStream(openArchive.getInputStream(entry));
										Enumeration<? extends ZipEntry> nestedZipEnumerator = new ZipEnumerator(zis);
										nestedZip = entry;
										openArchiveEnumeration.push(nestedZipEnumerator);
									}
								}
								openArchiveEnumeration.pop();
								if (nestedZip ==null) { openArchive = null; openFile = null; }
								else nestedZip = null;
							}
							openArchiveEnumeration = null;
							openArchive = null;
							openFile = null;
						} else if (openDirectoryEnumeration != null) {
							while (openDirectoryEnumeration.hasMoreElements()) {
								File entry = openDirectoryEnumeration.nextElement();
								String name = openDirectoryEnumeration.getName(entry);
								if (accept(name)) {
									nextEntry = new DirEntryJavaFileObject(openDirectoryEnumeration.getDirectory(), entry);
									return;
								}
							}
							openDirectoryEnumeration = null;
							openDirectory = null;
						} else if (openJrtEnumeration != null) {
							while (openJrtEnumeration.hasMoreElements()) {
								JrtEntryJavaFileObject jrtEntry = openJrtEnumeration.nextElement();
								String name = openJrtEnumeration.getName(jrtEntry);
								if (accept(name)) {
									nextEntry = jrtEntry;
									return;
								}
							}
							openJrtEnumeration = null;
							openJrt = null;
						}
					}
				} catch (IOException ioe) {
					logger.debug("Unexpected error whilst processing classpath entries",ioe);
				}
			}
		}

		public boolean hasNext() {
			findNext();
			return nextEntry != null;
		}

		public JavaFileObject next() {
			findNext();
			if (nextEntry == null) {
				throw new NoSuchElementException();
			}
			JavaFileObject retval = nextEntry;
			nextEntry = null;
			return retval;
		}

	}

	static class ZipEnumerator implements Enumeration<ZipEntry>{

		private ZipInputStream zis;
		private ZipEntry nextEntry = null;

		public ZipEnumerator(ZipInputStream zis) {
			this.zis = zis;
		}

		@Override
		public boolean hasMoreElements() {
			try {
				nextEntry = zis.getNextEntry();
			} catch (IOException ioe) {
				nextEntry=null;
			}
			return nextEntry!=null;
		}

		@Override
		public ZipEntry nextElement() {
			ZipEntry retval = nextEntry;
			nextEntry = null;
			return retval;
		}

	}

	public void reset() {
		close();
	}
}
