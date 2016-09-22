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

import java.util.Iterator;

import javax.tools.JavaFileObject;

import org.apache.commons.collections.IteratorUtils;

/**
 * Simple iterable that can be used to return an iterator over no values.
 * 
 * @author Andy Clement
 */
class EmptyIterable extends CloseableFilterableJavaFileObjectIterable {

	static EmptyIterable instance = new EmptyIterable();
	
	private EmptyIterable() {
		super(null,false);
	}
	
	public void close() {
	}

	@SuppressWarnings("unchecked")
	@Override
	public Iterator<JavaFileObject> iterator() {
		return IteratorUtils.emptyIterator();
	}

}