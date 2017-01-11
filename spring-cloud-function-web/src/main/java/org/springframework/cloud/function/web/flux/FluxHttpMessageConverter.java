/*
 * Copyright 2012-2015 the original author or authors.
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

package org.springframework.cloud.function.web.flux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import reactor.core.publisher.Flux;

/**
 * Converter for request bodies of type <code>Flux<String></code>.
 * 
 * @author Dave Syer
 *
 */
public class FluxHttpMessageConverter implements HttpMessageConverter<Flux<Object>> {

	private static final MediaType EVENT_STREAM = MediaType.valueOf("text/event-stream");

	@Override
	public boolean canRead(Class<?> clazz, MediaType mediaType) {
		return Flux.class.isAssignableFrom(clazz);
	}

	@Override
	public boolean canWrite(Class<?> clazz, MediaType mediaType) {
		return false;
	}

	@Override
	public List<MediaType> getSupportedMediaTypes() {
		return Arrays.asList(MediaType.ALL);
	}

	@Override
	public Flux<Object> read(Class<? extends Flux<Object>> clazz,
			HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException {

		MediaType mediaType = inputMessage.getHeaders().getContentType();
		if (mediaType != null) {
			if (mediaType.includes(MediaType.APPLICATION_JSON)) {
				return new JsonObjectDecoder().decode(inputMessage.getBody());
			}
			if (mediaType.includes(EVENT_STREAM)) {
				return splitOnSseData(inputMessage);
			}
		}

		return splitOnLineEndings(inputMessage);
	}

	private Flux<Object> splitOnLineEndings(HttpInputMessage inputMessage) {
		return Flux.create(sink -> {
			BufferedReader reader;
			try {
				reader = new BufferedReader(
						new InputStreamReader(inputMessage.getBody()));
				String line = reader.readLine();
				while (line != null) {
					sink.next(line);
					line = reader.readLine();
				}
			}
			catch (IOException e) {
				sink.error(e);
			}
			sink.complete();
		});
	}

	private Flux<Object> splitOnSseData(HttpInputMessage inputMessage) {
		return Flux.create(sink -> {
			BufferedReader reader;
			StringBuffer buffer = new StringBuffer();
			int emptyCount = 0;
			try {
				reader = new BufferedReader(
						new InputStreamReader(inputMessage.getBody()));
				String line = reader.readLine();
				while (line != null) {
					if (line.length() == 0) {
						emptyCount++;
					}
					else {
						if (buffer.length() == 0) {
							if (line.startsWith("data:")) {
								line = line.length() > "data:".length()
										? line.substring("data:".length()) : "";
							}
						}
						else {
							buffer.append("\n");
						}
						buffer.append(line);
					}
					if (emptyCount > 0) {
						sink.next(buffer.toString());
						buffer.setLength(0);
						emptyCount = 0;
						while (line != null && line.length() == 0) {
							line = reader.readLine();
						}
					}
					else {
						line = reader.readLine();
					}
				}
				if (buffer.length()>0) {
					sink.next(buffer.toString());
				}
			}
			catch (IOException e) {
				sink.error(e);
			}
			sink.complete();
		});
	}

	@Override
	public void write(Flux<Object> t, MediaType contentType,
			HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException {
	}

	static class JsonObjectDecoder {

		private static final int ST_CORRUPTED = -1;

		private static final int ST_INIT = 0;

		private static final int ST_DECODING_NORMAL = 1;

		private static final int ST_DECODING_ARRAY_STREAM = 2;

		private final int maxObjectLength = 1024 * 1024;

		private int openBraces;
		private int state;
		private boolean insideString;
		private int writerIndex;
		private boolean streamArrayElements = true;

		public Flux<Object> decode(InputStream body) {
			InputStreamReader reader = new InputStreamReader(body);
			char[] buffer = new char[1024];
			try {
				List<String> chunks = new ArrayList<>();
				int read = reader.read(buffer);
				this.writerIndex += read;
				while (read >= 0) {
					if (this.state == ST_CORRUPTED) {
						return Flux.error(new IllegalStateException("Corrupted stream"));
					}
					if (this.writerIndex > maxObjectLength) {
						// buffer size exceeded maxObjectLength; discarding the complete
						// buffer.
						reset();
						return Flux.error(new IllegalStateException(
								"object length exceeds " + maxObjectLength + ": "
										+ this.writerIndex + " bytes discarded"));
					}
					int point = 0;
					for (int index = 0; index < read; index++) {
						char c = buffer[index];
						if (this.state == ST_DECODING_NORMAL) {
							decodeByte(c, buffer, index);

							// All opening braces/brackets have been closed. That's enough
							// to conclude that the JSON object/array is complete.
							if (this.openBraces == 0) {
								char[] json = extractObject(buffer, point,
										index + 1 - point);
								if (json != null) {
									chunks.add(new String(json));
								}

								// The JSON object/array was extracted => discard the
								// bytes from the input buffer.
								point += index + 1 - point;
								// Reset the object state to get ready for the next JSON
								// object/text coming along the byte stream.
								reset();
							}
						}
						else if (this.state == ST_DECODING_ARRAY_STREAM) {
							decodeByte(c, buffer, index);

							if (!this.insideString && (this.openBraces == 1 && c == ','
									|| this.openBraces == 0 && c == ']')) {
								// skip leading spaces. No range check is needed and the
								// loop will terminate because the byte at position index
								// is not a whitespace.
								for (int i = point; Character
										.isWhitespace(buffer[i]); i++) {
									point++;
								}

								// skip trailing spaces.
								int idxNoSpaces = index - 1;
								while (idxNoSpaces >= 0
										&& Character.isWhitespace(buffer[idxNoSpaces])) {
									idxNoSpaces--;
								}

								char[] json = extractObject(buffer, point,
										idxNoSpaces + 1 - point);
								if (json != null) {
									chunks.add(new String(json));
								}

								point += index + 1 - point;

								if (c == ']') {
									reset();
								}
							}
							// JSON object/array detected. Accumulate bytes until all
							// braces/brackets are closed.
						}
						else if (c == '{' || c == '[') {
							initDecoding(c, this.streamArrayElements);

							if (this.state == ST_DECODING_ARRAY_STREAM) {
								// Discard the array bracket
								point++;
							}
							// Discard leading spaces in front of a JSON object/array.
						}
						else if (Character.isWhitespace(c)) {
							point++;
						}
						else {
							this.state = ST_CORRUPTED;
							return Flux.error(new IllegalStateException(
									"invalid JSON received at byte position "
											+ writerIndex));
						}
					}
					read = reader.read(buffer);
				}

				return Flux.fromIterable(chunks);
			}
			catch (IOException e) {
				return Flux.error(new IllegalStateException("Cannot read stream", e));
			}
		}

		private char[] extractObject(char[] buffer, int index, int length) {
			if (length <= 0) {
				return null;
			}
			return Arrays.copyOfRange(buffer, index, index + length);
		}

		private void decodeByte(char c, char[] input, int index) {
			if ((c == '{' || c == '[') && !this.insideString) {
				this.openBraces++;
			}
			else if ((c == '}' || c == ']') && !this.insideString) {
				this.openBraces--;
			}
			else if (c == '"') {
				// start of a new JSON string. It's necessary to detect strings as they
				// may also contain braces/brackets and that could lead to incorrect
				// results.
				if (!this.insideString) {
					this.insideString = true;
					// If the double quote wasn't escaped then this is the end of a
					// string.
				}
				else if (input[index - 1] != '\\') {
					this.insideString = false;
				}
			}
		}

		private void initDecoding(char openingBrace, boolean streamArrayElements) {
			this.openBraces = 1;
			if (openingBrace == '[' && streamArrayElements) {
				this.state = ST_DECODING_ARRAY_STREAM;
			}
			else {
				this.state = ST_DECODING_NORMAL;
			}
		}

		private void reset() {
			this.insideString = false;
			this.state = ST_INIT;
			this.openBraces = 0;
		}

	}

}
