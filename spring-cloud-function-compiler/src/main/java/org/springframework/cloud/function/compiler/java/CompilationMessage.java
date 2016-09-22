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

/**
 * Encapsulate information produced during compilation. A message may be an error
 * or something less serious (warning/informational). The <tt>toString()</tt> method
 * will produce a formatted error include source context indicating the precise
 * location of the problem.
 * 
 * @author Andy Clement
 */
public class CompilationMessage {
	
	private Kind kind;
	private String message;
	private String sourceCode;
	private int startPosition;
	private int endPosition;

	enum Kind {
		ERROR, OTHER
	};

	public CompilationMessage(Kind kind, String message, String sourceCode, int startPosition, int endPosition) {
		this.kind = kind;
		this.message = message;
		this.sourceCode = sourceCode;
		this.startPosition = startPosition;
		this.endPosition = endPosition;
	}

	/**
	 * @return the type of message
	 */
	public Kind getKind() {
		return this.kind;
	}

	/**
	 * @return the message text
	 */
	public String getMessage() {
		return this.message;
	}

	/**
	 * @return the source code for the file associated with the message
	 */
	public String getSourceCode() {
		return this.sourceCode;
	}

	/**
	 * @return offset from start of source file where the error begins
	 */
	public int getStartPosition() {
		return this.startPosition;
	}

	/**
	 * @return offset from start of source file where the error ends
	 */
	public int getEndPosition() {
		return this.endPosition;
	}

	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append("==========\n");
		if (sourceCode != null) { // Cannot include source context if no source available
			int[] lineStartEnd = getLineStartEnd(startPosition);
			s.append(sourceCode.substring(lineStartEnd[0], lineStartEnd[1])).append("\n");
			int col = lineStartEnd[0];
			// When inserting the whitespace, ensure tabs in the source line are respected
			while ((col) < startPosition) {
				s.append(sourceCode.charAt(col++)=='\t'?"\t":" ");
			}
			// Want at least one ^
			s.append("^");
			col++;
			while ((col++) < endPosition) {
				s.append("^");
			}
			s.append("\n");
		}
		s.append(kind).append(":").append(message).append("\n");
		s.append("==========\n");
		return s.toString();
	}

	/**
	 * For a given position in the source code this method returns a pair of int
	 * that indicate the start and end of the line within the source code that
	 * contain the position.
	 * 
	 * @param searchPos the position of interest in the source code
	 * @return an int array of length 2 containing the start and end positions of the line
	 */
	private int[] getLineStartEnd(int searchPos) {
		int previousPos = -1;
		int pos = 0;
		do {
			pos = sourceCode.indexOf('\n', previousPos + 1);
			if (searchPos < pos) {
				return new int[] { previousPos + 1, pos };
			}
			previousPos = pos;
		} while (pos != -1);
		return new int[] { previousPos + 1, sourceCode.length() };
	}
	// TODO test coverage for first line/last line situations

}
