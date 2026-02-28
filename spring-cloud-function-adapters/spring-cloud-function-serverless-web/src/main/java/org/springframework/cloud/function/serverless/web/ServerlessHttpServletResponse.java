/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.cloud.function.serverless.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.web.util.WebUtils;


/**
 *
 * @author Oleg Zhurakousky
 * @since 4.x
 *
 */
public class ServerlessHttpServletResponse implements HttpServletResponse {

	private static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

	private String defaultCharacterEncoding = WebUtils.DEFAULT_CHARACTER_ENCODING;

	private String characterEncoding = this.defaultCharacterEncoding;

	private final ByteArrayOutputStream content = new ByteArrayOutputStream(1024);

	private final ServletOutputStream outputStream = new ResponseServletOutputStream();

	private String contentType;

	private int bufferSize = 4096;

	private Locale locale = Locale.getDefault();

	private final List<Cookie> cookies = new ArrayList<>();

	private final HttpHeaders headers = new HttpHeaders();

	private int status = HttpServletResponse.SC_OK;

	private ResponsePrintWriter writer;

	@Nullable
	private String errorMessage;

	@Override
	public void setCharacterEncoding(String characterEncoding) {
		this.characterEncoding = characterEncoding;
	}

	@Override
	public void setCharacterEncoding(Charset encoding) {
		HttpServletResponse.super.setCharacterEncoding(encoding);
	}

	@Override
	public String getCharacterEncoding() {
		return this.characterEncoding;
	}

	@Override
	public ServletOutputStream getOutputStream() {
		return this.outputStream;
	}

	@Override
	public PrintWriter getWriter() throws UnsupportedEncodingException {
		if (this.writer == null) {
			Writer targetWriter = new OutputStreamWriter(this.content, getCharacterEncoding());
			this.writer = new ResponsePrintWriter(targetWriter);
		}
		return this.writer;
	}

	public byte[] getContentAsByteArray() {
		return this.content.toByteArray();
	}

	/**
	 * Get the content of the response body as a {@code String}, using the charset
	 * specified for the response by the application, either through
	 * {@link HttpServletResponse} methods or through a charset parameter on the
	 * {@code Content-Type}. If no charset has been explicitly defined, the
	 * {@linkplain #setDefaultCharacterEncoding(String) default character encoding}
	 * will be used.
	 *
	 * @return the content as a {@code String}
	 * @throws UnsupportedEncodingException if the character encoding is not
	 *                                      supported
	 * @see #getContentAsString(Charset)
	 * @see #setCharacterEncoding(String)
	 * @see #setContentType(String)
	 */
	public String getContentAsString() throws UnsupportedEncodingException {
		return this.content.toString(getCharacterEncoding());
	}

	public String getContentAsString(Charset fallbackCharset) throws UnsupportedEncodingException {
		return this.content.toString(getCharacterEncoding());
	}

	@Override
	public void setContentLength(int contentLength) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setContentLengthLong(long len) {
		// Ignore
	}

	@Override
	public void setContentType(@Nullable String contentType) {
		this.contentType = contentType;
	}

	@Override
	@Nullable
	public String getContentType() {
		return this.contentType;
	}

	@Override
	public void setBufferSize(int bufferSize) {
		this.bufferSize = bufferSize;
	}

	@Override
	public int getBufferSize() {
		return this.bufferSize;
	}

	@Override
	public void flushBuffer() {
	}

	@Override
	public void resetBuffer() {
		Assert.state(!isCommitted(), "Cannot reset buffer - response is already committed");
		this.content.reset();
	}

	@Override
	public boolean isCommitted() {
		return this.writer == null ? false : this.writer.commited;
	}

	@Override
	public void reset() {
		resetBuffer();
		this.characterEncoding = this.defaultCharacterEncoding;
		this.contentType = null;
		this.locale = Locale.getDefault();
		this.cookies.clear();
		this.headers.clear();
		this.status = HttpServletResponse.SC_OK;
		this.errorMessage = null;
	}

	@Override
	public void setLocale(@Nullable Locale locale) {
		if (locale == null) {
			return;
		}
		this.locale = locale;
		this.headers.add(HttpHeaders.CONTENT_LANGUAGE, locale.toLanguageTag());
	}

	@Override
	public Locale getLocale() {
		return this.locale;
	}

	// ---------------------------------------------------------------------
	// HttpServletResponse interface
	// ---------------------------------------------------------------------

	@Override
	public void addCookie(Cookie cookie) {
		throw new UnsupportedOperationException();
	}

	@Nullable
	public Cookie getCookie(String name) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean containsHeader(String name) {
		return this.headers.containsHeader(name);
	}

	/**
	 * Return the names of all specified headers as a Set of Strings.
	 * <p>
	 * As of Servlet 3.0, this method is also defined in
	 * {@link HttpServletResponse}.
	 *
	 * @return the {@code Set} of header name {@code Strings}, or an empty
	 *         {@code Set} if none
	 */
	@Override
	public Collection<String> getHeaderNames() {
		return this.headers.headerNames();
	}

	@Override
	public void setTrailerFields(Supplier<Map<String, String>> supplier) {
		HttpServletResponse.super.setTrailerFields(supplier);
	}

	@Override
	public Supplier<Map<String, String>> getTrailerFields() {
		return HttpServletResponse.super.getTrailerFields();
	}

	/**
	 * Return the primary value for the given header as a String, if any. Will
	 * return the first value in case of multiple values.
	 * <p>
	 * As of Servlet 3.0, this method is also defined in
	 * {@link HttpServletResponse}. As of Spring 3.1, it returns a stringified value
	 * for Servlet 3.0 compatibility. Consider using {@link #getHeaderValue(String)}
	 * for raw Object access.
	 *
	 * @param name the name of the header
	 * @return the associated header value, or {@code null} if none
	 */
	@Override
	@Nullable
	public String getHeader(String name) {
		return this.headers.containsHeader(name) ? this.headers.get(name).get(0) : null;
	}

	/**
	 * Return all values for the given header as a List of Strings.
	 * <p>
	 * As of Servlet 3.0, this method is also defined in
	 * {@link HttpServletResponse}. As of Spring 3.1, it returns a List of
	 * stringified values for Servlet 3.0 compatibility. Consider using
	 * {@link #getHeaders(String)} for raw Object access.
	 *
	 * @param name the name of the header
	 * @return the associated header values, or an empty List if none
	 */
	@Override
	public List<String> getHeaders(String name) {
		if (!this.headers.containsHeader(name)) {
			return Collections.emptyList();
		}
		return this.headers.get(name);
	}

	/**
	 * Return the primary value for the given header, if any.
	 * <p>
	 * Will return the first value in case of multiple values.
	 *
	 * @param name the name of the header
	 * @return the associated header value, or {@code null} if none
	 */
	@Nullable
	public Object getHeaderValue(String name) {
		return this.headers.containsHeader(name) ? this.headers.get(name).get(0) : null;
	}

	/**
	 * The default implementation returns the given URL String as-is.
	 * <p>
	 * Can be overridden in subclasses, appending a session id or the like.
	 */
	@Override
	public String encodeURL(String url) {
		return url;
	}

	/**
	 * The default implementation delegates to {@link #encodeURL}, returning the
	 * given URL String as-is.
	 * <p>
	 * Can be overridden in subclasses, appending a session id or the like in a
	 * redirect-specific fashion. For general URL encoding rules, override the
	 * common {@link #encodeURL} method instead, applying to redirect URLs as well
	 * as to general URLs.
	 */
	@Override
	public String encodeRedirectURL(String url) {
		return encodeURL(url);
	}

	@Override
	public void sendError(int status, String errorMessage) throws IOException {
		Assert.state(!isCommitted(), "Cannot set error status - response is already committed");
		this.status = status;
		this.errorMessage = errorMessage;
	}

	@Override
	public void sendError(int status) throws IOException {
		Assert.state(!isCommitted(), "Cannot set error status - response is already committed");
		this.status = status;
	}

	@Override
	public void sendRedirect(String url) throws IOException {
		Assert.state(!isCommitted(), "Cannot send redirect - response is already committed");
		Assert.notNull(url, "Redirect URL must not be null");
		setHeader(HttpHeaders.LOCATION, url);
		setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
	}


	@Override
	public void sendRedirect(String location, int sc, boolean clearBuffer) throws IOException {
		Assert.state(!isCommitted(), "Cannot send redirect - response is already committed");
		Assert.notNull(location, "Redirect location must not be null");
		setHeader(HttpHeaders.LOCATION, location);
		setStatus(sc);
	}

	@Nullable
	public String getRedirectedUrl() {
		return getHeader(HttpHeaders.LOCATION);
	}

	@Override
	public void setDateHeader(String name, long value) {
		this.headers.set(name, formatDate(value));
	}

	@Override
	public void addDateHeader(String name, long value) {
		this.headers.add(name, formatDate(value));
	}

	private String formatDate(long date) {
		return newDateFormat().format(new Date(date));
	}

	private DateFormat newDateFormat() {
		SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.US);
		return dateFormat;
	}

	@Override
	public void setHeader(String name, @Nullable String value) {
		this.headers.set(name, value);
	}

	@Override
	public void addHeader(String name, @Nullable String value) {
		this.headers.add(name, value);
	}

	@Override
	public void setIntHeader(String name, int value) {
		this.headers.set(name, String.valueOf(value));
	}

	@Override
	public void addIntHeader(String name, int value) {
		this.headers.add(name, String.valueOf(value));
	}

	@Override
	public void setStatus(int status) {
		if (!this.isCommitted()) {
			this.status = status;
		}
	}


	@Override
	public int getStatus() {
		return this.status;
	}

	@Nullable
	public String getErrorMessage() {
		return this.errorMessage;
	}

	/**
	 * Inner class that adapts the ServletOutputStream to mark the response as
	 * committed once the buffer size is exceeded.
	 */
	private final class ResponseServletOutputStream extends ServletOutputStream {

		private WriteListener listener;

		@Override
		public boolean isReady() {
			return true;
		}

		@Override
		public void setWriteListener(WriteListener writeListener) {
			if (writeListener != null) {
				try {
					writeListener.onWritePossible();
				}
				catch (IOException e) {
					// log.error("Output stream is not writable", e);
				}

				listener = writeListener;
			}
		}

		@Override
		public void write(int b) throws IOException {
			try {
				content.write(b);
			}
			catch (Exception e) {
				if (listener != null) {
					listener.onError(e);
				}
			}
		}

		@Override
		public void close() throws IOException {
			super.close();
			flushBuffer();
		}
	}

	private class ResponsePrintWriter extends PrintWriter {

		private boolean commited;

		ResponsePrintWriter(Writer out) {
			super(out, true);
		}

		@Override
		public void write(char[] buf, int off, int len) {
			super.write(buf, off, len);
			super.flush();
			this.commited = true;
		}

		@Override
		public void write(String s, int off, int len) {
			super.write(s, off, len);
			super.flush();
			this.commited = true;
		}

		@Override
		public void write(int c) {
			super.write(c);
			super.flush();
			this.commited = true;
		}

		@Override
		public void flush() {
			super.flush();
			this.commited = true;
		}

		@Override
		public void close() {
			super.flush();
			super.close();
			this.commited = true;
		}
	}

}
