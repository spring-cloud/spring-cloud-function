/*
 * Copyright 2016 the original author or authors.
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

/**
 * Encapsulates a name with the bytes for its class definition.
 * 
 * @author Andy Clement
 */
public class CompiledClassDefinition {

	private byte[] bytes;
	private String filename;
	private String classname;

	public CompiledClassDefinition(String filename, byte[] bytes) {
		this.filename = filename;
		this.bytes = bytes;
		this.classname = filename;
		if (classname.startsWith(File.separator)) {
			classname = classname.substring(1);
		}
		classname = classname.replace(File.separatorChar, '.').substring(0, classname.length()-6);//strip off .class
	}

	public String getName() {
		return filename;
	}

	public byte[] getBytes() {
		return bytes;
	}

	public String toString() {
		return "CompiledClassDefinition(name=" + getName() + ",#bytes=" + getBytes().length + ")";
	}

	public String getClassName() {
		return this.classname;
	}

}