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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Walks a directory hierarchy from some base directory discovering files.
 *
 * @author Andy Clement
 */
public class DirEnumeration implements Enumeration<File> {

	// The starting point
	private File basedir;

	// Candidates collected so far
	private List<File> filesToReturn;

	// Places still to explore for candidates
	private List<File> directoriesToExplore;

	public DirEnumeration(File basedir) {
		this.basedir = basedir;
	}

	private void computeValue() {
		if (filesToReturn == null) { // Indicates we haven't started yet
			filesToReturn = new ArrayList<>();
			directoriesToExplore = new ArrayList<>();
			visitDirectory(basedir);
		}
		if (filesToReturn.size() == 0) {
			while (filesToReturn.size() == 0 && directoriesToExplore.size() != 0) {
				File nextDir = directoriesToExplore.get(0);
				directoriesToExplore.remove(0);
				visitDirectory(nextDir);
			}
		}
	}

	@Override
	public boolean hasMoreElements() {
		computeValue();
		return filesToReturn.size() != 0;
	}

	@Override
	public File nextElement() {
		computeValue();
		if (filesToReturn.size()==0) {
			throw new NoSuchElementException();
		}
		File toReturn = filesToReturn.get(0);
		filesToReturn.remove(0);
		return toReturn;
	}

	private void visitDirectory(File dir) {
		File[] files = dir.listFiles();
		if (files != null) {
			for (File file: files) {
				if (file.isDirectory()) {
					directoriesToExplore.add(file);
				} else {
					filesToReturn.add(file);
				}
			}
		}
	}

	public File getDirectory() {
		return basedir;
	}

	/**
	 * Return the relative path of this file to the base directory that the directory enumeration was
	 * started for.
	 * @param file a file discovered returned by this enumeration
	 * @return the relative path of the file (for example: a/b/c/D.class)
	 */
	public String getName(File file) {
		String basedirPath = basedir.getPath();
		String filePath = file.getPath();
		if (!filePath.startsWith(basedirPath)) {
			throw new IllegalStateException("The file '"+filePath+"' is not nested below the base directory '"+basedirPath+"'");
		}
		return filePath.substring(basedirPath.length()+1);
	}

}
