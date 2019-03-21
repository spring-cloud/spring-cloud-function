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
 * Iterable that will produce an iterator that returns classes found on a specified
 * classpath that meet specified criteria. For jars it finds, the iterator will go into
 * nested jars - this handles the situation with a spring boot uberjar.
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
	 * @param packageNameFilter an optional package name if choosing to filter (e.g.
	 * com.example)
	 * @param includeSubpackages if true, include results in subpackages of the specified
	 * package filter
	 */
	IterableClasspath(CompilationInfoCache compilationInfoCache, String classpath,
			String packageNameFilter, boolean includeSubpackages) {
		super(compilationInfoCache, packageNameFilter, includeSubpackages);
		StringTokenizer tokenizer = new StringTokenizer(classpath, File.pathSeparator);
		while (tokenizer.hasMoreElements()) {
			String nextEntry = tokenizer.nextToken();
			File f = new File(nextEntry);
			if (f.exists()) {
				// Skip iterating over archives that cannot possibly match the filter
				if (this.packageNameFilter != null
						&& this.packageNameFilter.length() > 0) {
					ArchiveInfo archiveInfo = compilationInfoCache.getArchiveInfoFor(f);
					if (archiveInfo != null
							&& !archiveInfo.containsPackage(this.packageNameFilter,
									this.includeSubpackages)) {
						continue;
					}
				}
				this.classpathEntries.add(f);
			}
			else {
				logger.debug("path element does not exist {}", f);
			}
		}
	}

	public void close() {
		for (ZipFile openArchive : this.openArchives) {
			try {
				openArchive.close();
			}
			catch (IOException ioe) {
				logger.debug("Unexpected error closing archive {}", openArchive, ioe);
			}
		}
		this.openArchives.clear();
	}

	public Iterator<JavaFileObject> iterator() {
		return new ClasspathEntriesIterator();
	}

	public void reset() {
		close();
	}

	static class ZipEnumerator implements Enumeration<ZipEntry> {

		private ZipInputStream zis;

		private ZipEntry nextEntry = null;

		ZipEnumerator(ZipInputStream zis) {
			this.zis = zis;
		}

		@Override
		public boolean hasMoreElements() {
			try {
				this.nextEntry = this.zis.getNextEntry();
			}
			catch (IOException ioe) {
				this.nextEntry = null;
			}
			return this.nextEntry != null;
		}

		@Override
		public ZipEntry nextElement() {
			ZipEntry retval = this.nextEntry;
			this.nextEntry = null;
			return retval;
		}

	}

	class ClasspathEntriesIterator implements Iterator<JavaFileObject> {

		private int currentClasspathEntriesIndex = 0;

		// Walking one of three possible things: directory tree, zip, or Java runtime
		// packaged in JDK9+ form
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
			if (this.nextEntry == null) {
				try {
					while (this.openArchive != null || this.openDirectory != null
							|| this.openJrt != null
							|| this.currentClasspathEntriesIndex < IterableClasspath.this.classpathEntries
									.size()) {
						if (this.openArchive == null && this.openDirectory == null
								&& this.openJrt == null) {
							// Open the next item
							File nextFile = IterableClasspath.this.classpathEntries
									.get(this.currentClasspathEntriesIndex);
							if (nextFile.isDirectory()) {
								this.openDirectory = nextFile;
								this.openDirectoryEnumeration = new DirEnumeration(
										nextFile);
							}
							else if (nextFile.getName().endsWith("jrt-fs.jar")) {
								this.openJrt = nextFile;
								this.openJrtEnumeration = new JrtFsEnumeration(nextFile,
										null);
							}
							else {
								this.openFile = nextFile;
								this.openArchive = new ZipFile(nextFile);
								IterableClasspath.this.openArchives.add(this.openArchive);
								this.openArchiveEnumeration = new Stack<Enumeration<? extends ZipEntry>>();
								this.openArchiveEnumeration
										.push(this.openArchive.entries());
							}
							this.currentClasspathEntriesIndex++;
						}
						if (this.openArchiveEnumeration != null) {
							while (!this.openArchiveEnumeration.isEmpty()) {
								while (this.openArchiveEnumeration.peek()
										.hasMoreElements()) {
									ZipEntry entry = this.openArchiveEnumeration.peek()
											.nextElement();
									String entryName = entry.getName();
									if (accept(entryName)) {
										if (this.nestedZip != null) {
											this.nextEntry = new NestedZipEntryJavaFileObject(
													this.openFile, this.openArchive,
													this.nestedZip, entry);
										}
										else {
											this.nextEntry = new ZipEntryJavaFileObject(
													this.openFile, this.openArchive,
													entry);
										}
										return;
									}
									else if (this.nestedZip == null
											&& entryName.startsWith(
													MemoryBasedJavaFileManager.BOOT_PACKAGING_PREFIX_FOR_LIBRARIES)
											&& entryName.endsWith(".jar")) {
										// nested jar in uber jar
										logger.debug("opening nested archive {}",
												entry.getName());
										ZipInputStream zis = new ZipInputStream(
												this.openArchive.getInputStream(entry));
										Enumeration<? extends ZipEntry> nestedZipEnumerator = new ZipEnumerator(
												zis);
										this.nestedZip = entry;
										this.openArchiveEnumeration
												.push(nestedZipEnumerator);
									}
								}
								this.openArchiveEnumeration.pop();
								if (this.nestedZip == null) {
									this.openArchive = null;
									this.openFile = null;
								}
								else {
									this.nestedZip = null;
								}
							}
							this.openArchiveEnumeration = null;
							this.openArchive = null;
							this.openFile = null;
						}
						else if (this.openDirectoryEnumeration != null) {
							while (this.openDirectoryEnumeration.hasMoreElements()) {
								File entry = this.openDirectoryEnumeration.nextElement();
								String name = this.openDirectoryEnumeration
										.getName(entry);
								if (accept(name)) {
									this.nextEntry = new DirEntryJavaFileObject(
											this.openDirectoryEnumeration.getDirectory(),
											entry);
									return;
								}
							}
							this.openDirectoryEnumeration = null;
							this.openDirectory = null;
						}
						else if (this.openJrtEnumeration != null) {
							while (this.openJrtEnumeration.hasMoreElements()) {
								JrtEntryJavaFileObject jrtEntry = this.openJrtEnumeration
										.nextElement();
								String name = this.openJrtEnumeration.getName(jrtEntry);
								if (accept(name)) {
									this.nextEntry = jrtEntry;
									return;
								}
							}
							this.openJrtEnumeration = null;
							this.openJrt = null;
						}
					}
				}
				catch (IOException ioe) {
					logger.debug("Unexpected error whilst processing classpath entries",
							ioe);
				}
			}
		}

		public boolean hasNext() {
			findNext();
			return this.nextEntry != null;
		}

		public JavaFileObject next() {
			findNext();
			if (this.nextEntry == null) {
				throw new NoSuchElementException();
			}
			JavaFileObject retval = this.nextEntry;
			this.nextEntry = null;
			return retval;
		}

	}

}
