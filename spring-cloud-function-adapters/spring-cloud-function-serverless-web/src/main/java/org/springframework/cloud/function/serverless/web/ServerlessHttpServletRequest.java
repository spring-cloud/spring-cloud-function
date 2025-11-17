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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ReadListener;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;

import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * @author Oleg Zhurakousky
 *
 */
public class ServerlessHttpServletRequest implements HttpServletRequest {

	private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

	private static final BufferedReader EMPTY_BUFFERED_READER = new BufferedReader(new StringReader(""));

	private static final InputStream EMPTY_INPUT_STREAM = new ByteArrayInputStream(new byte[0]);

	/**
	 * Date formats as specified in the HTTP RFC.
	 *
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-7.1.1.1">Section 7.1.1.1
	 * of RFC 7231</a>
	 */
	private static final String[] DATE_FORMATS = new String[] { "EEE, dd MMM yyyy HH:mm:ss zzz",
			"EEE, dd-MMM-yy HH:mm:ss zzz", "EEE MMM dd HH:mm:ss yyyy" };

	private final ServletContext servletContext;

	// ---------------------------------------------------------------------
	// ServletRequest properties
	// ---------------------------------------------------------------------

	private final Map<String, Object> attributes = new LinkedHashMap<>();

	@Nullable
	private String characterEncoding;

	@Nullable
	private byte[] content;

	@Nullable
	private ServletInputStream inputStream;

	@Nullable
	private BufferedReader reader;

	private final Map<String, String[]> parameters = new LinkedHashMap<>(16);

	/** List of locales in descending order. */
	private final LinkedList<Locale> locales = new LinkedList<>();

	private boolean asyncStarted = false;

	private boolean asyncSupported = true;

	private DispatcherType dispatcherType = DispatcherType.REQUEST;

	@Nullable
	private String authType;

	@Nullable
	private Cookie[] cookies;

	private final HttpHeaders headers = new HttpHeaders();

	@Nullable
	private String method;

	@Nullable
	private String pathInfo;

	private String contextPath = "";

	@Nullable
	private String queryString;

	@Nullable
	private String remoteUser;

	private final Set<String> userRoles = new HashSet<>();

	@Nullable
	private Principal userPrincipal;

	@Nullable
	private String requestedSessionId;

	@Nullable
	private String requestURI;

	private String servletPath = "";

	@Nullable
	private HttpSession session;

	private boolean requestedSessionIdValid = true;

	private boolean requestedSessionIdFromCookie = true;

	private boolean requestedSessionIdFromURL = false;

	private final MultiValueMap<String, Part> parts = new LinkedMultiValueMap<>();

	private AsyncContext asyncContext;

	public ServerlessHttpServletRequest(ServletContext servletContext, String method, String requestURI) {
		this.servletContext = servletContext;
		this.method = method;
		this.requestURI = requestURI;
		this.pathInfo = requestURI;
		this.locales.add(Locale.ENGLISH);
	}

	@Override
	public String toString() {
		return "Method: " + this.method + ", RequestURI: " + this.requestURI;
	}

	/**
	 * Return the ServletContext that this request is associated with. (Not available in
	 * the standard HttpServletRequest interface for some reason.)
	 */
	@Override
	public ServletContext getServletContext() {
		return this.servletContext;
	}

	@Override
	public Object getAttribute(String name) {
		return this.attributes.get(name);
	}

	@Override
	public Enumeration<String> getAttributeNames() {
		return Collections.enumeration(new LinkedHashSet<>(this.attributes.keySet()));
	}

	@Override
	@Nullable
	public String getCharacterEncoding() {
		return this.characterEncoding;
	}

	@Override
	public void setCharacterEncoding(@Nullable String characterEncoding) {
		this.characterEncoding = characterEncoding;
	}

	/**
	 * Set the content of the request body as a byte array.
	 * <p>
	 * If the supplied byte array represents text such as XML or JSON, the
	 * {@link #setCharacterEncoding character encoding} should typically be set as well.
	 *
	 * @see #setCharacterEncoding(String)
	 * @see #getContentAsByteArray()
	 * @see #getContentAsString()
	 */
	public void setContent(@Nullable byte[] content) {
		this.content = content;
		this.inputStream = null;
		this.reader = null;
	}

	/**
	 * Get the content of the request body as a byte array.
	 * @return the content as a byte array (potentially {@code null})
	 * @since 5.0
	 * @see #setContent(byte[])
	 * @see #getContentAsString()
	 */
	@Nullable
	public byte[] getContentAsByteArray() {
		return this.content;
	}

	/**
	 * Get the content of the request body as a {@code String}, using the configured
	 * {@linkplain #getCharacterEncoding character encoding}.
	 * @return the content as a {@code String}, potentially {@code null}
	 * @throws IllegalStateException if the character encoding has not been set
	 * @throws UnsupportedEncodingException if the character encoding is not supported
	 * @since 5.0
	 * @see #setContent(byte[])
	 * @see #setCharacterEncoding(String)
	 * @see #getContentAsByteArray()
	 */
	@Nullable
	public String getContentAsString() throws IllegalStateException, UnsupportedEncodingException {

		if (this.content == null) {
			return null;
		}
		return new String(this.content, StandardCharsets.UTF_8);
	}

	@Override
	public int getContentLength() {
		return (this.content != null ? this.content.length : -1);
	}

	@Override
	public long getContentLengthLong() {
		return getContentLength();
	}

	public void setContentType(@Nullable String contentType) {
		this.headers.set(HttpHeaders.CONTENT_TYPE, contentType);
	}

	@Override
	@Nullable
	public String getContentType() {
		return this.headers.containsHeader(HttpHeaders.CONTENT_TYPE) ? this.headers.get(HttpHeaders.CONTENT_TYPE).get(0)
				: null;
	}

	@Override
	public ServletInputStream getInputStream() {

		InputStream stream;
		if (this.content == null) {
			stream = EMPTY_INPUT_STREAM;
		}
		else {
			stream = new ByteArrayInputStream(this.content);
		}

		return new ServletInputStream() {

			boolean finished = false;

			@Override
			public int read() throws IOException {
				int readByte = stream.read();
				if (readByte == -1) {
					finished = true;
				}
				return readByte;
			}

			@Override
			public void setReadListener(ReadListener readListener) {
			}

			@Override
			public boolean isReady() {
				return !finished;
			}

			@Override
			public boolean isFinished() {
				return finished;
			}
		};
	}

	/**
	 * Set a single value for the specified HTTP parameter.
	 * <p>
	 * If there are already one or more values registered for the given parameter name,
	 * they will be replaced.
	 */
	public void setParameter(String name, String value) {
		setParameter(name, new String[] { value });
	}

	/**
	 * Set an array of values for the specified HTTP parameter.
	 * <p>
	 * If there are already one or more values registered for the given parameter name,
	 * they will be replaced.
	 */
	public void setParameter(String name, String... values) {
		Assert.notNull(name, "Parameter name must not be null");
		this.parameters.put(name, values);
	}

	/**
	 * Set all provided parameters <strong>replacing</strong> any existing values for the
	 * provided parameter names. To add without replacing existing values, use
	 * {@link #addParameters(java.util.Map)}.
	 */
	public void setParameters(Map<String, ?> params) {
		Assert.notNull(params, "Parameter map must not be null");
		params.forEach((key, value) -> {
			if (value instanceof String) {
				setParameter(key, (String) value);
			}
			else if (value instanceof String[]) {
				setParameter(key, (String[]) value);
			}
			else {
				throw new IllegalArgumentException("Parameter map value must be single value " + " or array of type ["
						+ String.class.getName() + "]");
			}
		});
	}

	/**
	 * Add a single value for the specified HTTP parameter.
	 * <p>
	 * If there are already one or more values registered for the given parameter name,
	 * the given value will be added to the end of the list.
	 */
	public void addParameter(String name, @Nullable String value) {
		addParameter(name, new String[] { value });
	}

	/**
	 * Add an array of values for the specified HTTP parameter.
	 * <p>
	 * If there are already one or more values registered for the given parameter name,
	 * the given values will be added to the end of the list.
	 */
	public void addParameter(String name, String... values) {
		Assert.notNull(name, "Parameter name must not be null");
		String[] oldArr = this.parameters.get(name);
		if (oldArr != null) {
			String[] newArr = new String[oldArr.length + values.length];
			System.arraycopy(oldArr, 0, newArr, 0, oldArr.length);
			System.arraycopy(values, 0, newArr, oldArr.length, values.length);
			this.parameters.put(name, newArr);
		}
		else {
			this.parameters.put(name, values);
		}
	}

	/**
	 * Add all provided parameters <strong>without</strong> replacing any existing values.
	 * To replace existing values, use {@link #setParameters(java.util.Map)}.
	 */
	public void addParameters(Map<String, ?> params) {
		Assert.notNull(params, "Parameter map must not be null");
		params.forEach((key, value) -> {
			if (value instanceof String) {
				addParameter(key, (String) value);
			}
			else if (value instanceof String[]) {
				addParameter(key, (String[]) value);
			}
			else {
				throw new IllegalArgumentException("Parameter map value must be single value " + " or array of type ["
						+ String.class.getName() + "]");
			}
		});
	}

	/**
	 * Remove already registered values for the specified HTTP parameter, if any.
	 */
	public void removeParameter(String name) {
		Assert.notNull(name, "Parameter name must not be null");
		this.parameters.remove(name);
	}

	/**
	 * Remove all existing parameters.
	 */
	public void removeAllParameters() {
		this.parameters.clear();
	}

	@Override
	@Nullable
	public String getParameter(String name) {
		Assert.notNull(name, "Parameter name must not be null");
		String[] arr = this.parameters.get(name);
		return (arr != null && arr.length > 0 ? arr[0] : null);
	}

	@Override
	public Enumeration<String> getParameterNames() {
		return Collections.enumeration(this.parameters.keySet());
	}

	@Override
	public String[] getParameterValues(String name) {
		Assert.notNull(name, "Parameter name must not be null");
		return this.parameters.get(name);
	}

	@Override
	public Map<String, String[]> getParameterMap() {
		return Collections.unmodifiableMap(this.parameters);
	}

	@Override
	public String getProtocol() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getScheme() {
		return "https";
	}

	public void setServerName(String serverName) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getServerName() {
		return "spring-serverless-web-proxy";
	}

	public void setServerPort(int serverPort) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getServerPort() {
		return 0;
	}

	@Override
	public BufferedReader getReader() throws UnsupportedEncodingException {
		if (this.reader != null) {
			return this.reader;
		}
		else if (this.inputStream != null) {
			throw new IllegalStateException(
					"Cannot call getReader() after getInputStream() has already been called for the current request");
		}

		if (this.content != null) {
			InputStream sourceStream = new ByteArrayInputStream(this.content);
			Reader sourceReader = (this.characterEncoding != null)
					? new InputStreamReader(sourceStream, this.characterEncoding) : new InputStreamReader(sourceStream);
			this.reader = new BufferedReader(sourceReader);
		}
		else {
			this.reader = EMPTY_BUFFERED_READER;
		}
		return this.reader;
	}

	public void setRemoteAddr(String remoteAddr) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getRemoteAddr() {
		return "proxy";
	}

	public void setRemoteHost(String remoteHost) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getRemoteHost() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAttribute(String name, @Nullable Object value) {
		Assert.notNull(name, "Attribute name must not be null");
		if (value != null) {
			this.attributes.put(name, value);
		}
		else {
			this.attributes.remove(name);
		}
	}

	@Override
	public void removeAttribute(String name) {
		Assert.notNull(name, "Attribute name must not be null");
		this.attributes.remove(name);
	}

	/**
	 * Clear all of this request's attributes.
	 */
	public void clearAttributes() {
		this.attributes.clear();
	}

	/**
	 * Return the first preferred {@linkplain Locale locale} configured in this mock
	 * request.
	 * <p>
	 * If no locales have been explicitly configured, the default, preferred
	 * {@link Locale} for the <em>server</em> mocked by this request is
	 * {@link Locale#ENGLISH}.
	 * <p>
	 * In contrast to the Servlet specification, this mock implementation does
	 * <strong>not</strong> take into consideration any locales specified via the
	 * {@code Accept-Language} header.
	 *
	 * @see javax.servlet.ServletRequest#getLocale()
	 * @see #addPreferredLocale(Locale)
	 * @see #setPreferredLocales(List)
	 */
	@Override
	public Locale getLocale() {
		return this.locales.getFirst();
	}

	/**
	 * Return an {@linkplain Enumeration enumeration} of the preferred {@linkplain Locale
	 * locales} configured in this mock request.
	 * <p>
	 * If no locales have been explicitly configured, the default, preferred
	 * {@link Locale} for the <em>server</em> mocked by this request is
	 * {@link Locale#ENGLISH}.
	 * <p>
	 * In contrast to the Servlet specification, this mock implementation does
	 * <strong>not</strong> take into consideration any locales specified via the
	 * {@code Accept-Language} header.
	 *
	 * @see javax.servlet.ServletRequest#getLocales()
	 * @see #addPreferredLocale(Locale)
	 * @see #setPreferredLocales(List)
	 */
	@Override
	public Enumeration<Locale> getLocales() {
		return Collections.enumeration(this.locales);
	}

	/**
	 * Return {@code true} if the {@link #setSecure secure} flag has been set to
	 * {@code true} or if the {@link #getScheme scheme} is {@code https}.
	 *
	 * @see javax.servlet.ServletRequest#isSecure()
	 */
	@Override
	public boolean isSecure() {
		return false;
	}

	@Override
	public RequestDispatcher getRequestDispatcher(String path) {
		throw new UnsupportedOperationException();
	}

	public void setRemotePort(int remotePort) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getRemotePort() {
		throw new UnsupportedOperationException();
	}

	public void setLocalName(String localName) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getLocalName() {
		throw new UnsupportedOperationException();
	}

	public void setLocalAddr(String localAddr) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getLocalAddr() {
		return "proxy";
	}

	public void setLocalPort(int localPort) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getLocalPort() {
		throw new UnsupportedOperationException();
	}

	@Override
	public AsyncContext startAsync() {
		return startAsync(this, null);
	}

	@Override
	public AsyncContext startAsync(ServletRequest request, @Nullable ServletResponse response) {
		Assert.state(this.asyncSupported, "Async not supported");
		this.dispatcherType = DispatcherType.ASYNC;
		this.asyncStarted = true;
		this.asyncContext = this.asyncContext == null ? new ServerlessAsyncContext(request, response)
				: this.asyncContext;
		return this.asyncContext;
	}

	public void setAsyncStarted(boolean asyncStarted) {
		this.asyncStarted = asyncStarted;
	}

	@Override
	public boolean isAsyncStarted() {
		return this.asyncStarted;
	}

	public void setAsyncSupported(boolean asyncSupported) {
		this.asyncSupported = asyncSupported;
		this.dispatcherType = DispatcherType.ASYNC;
	}

	@Override
	public boolean isAsyncSupported() {
		return this.asyncSupported;
	}

	public void setAsyncContext(@Nullable AsyncContext asyncContext) {
		this.asyncContext = asyncContext;
	}

	@Override
	@Nullable
	public AsyncContext getAsyncContext() {
		return this.asyncContext;
	}

	public void setDispatcherType(DispatcherType dispatcherType) {
		this.dispatcherType = dispatcherType;
	}

	@Override
	public DispatcherType getDispatcherType() {
		return this.dispatcherType;
	}

	public void setAuthType(@Nullable String authType) {
		this.authType = authType;
	}

	@Override
	@Nullable
	public String getAuthType() {
		return this.authType;
	}

	@Override
	@Nullable
	public Cookie[] getCookies() {
		return this.cookies;
	}

	@Override
	@Nullable
	public String getHeader(String name) {
		return this.headers.containsHeader(name) ? this.headers.get(name).get(0) : null;
	}

	@Override
	public Enumeration<String> getHeaders(String name) {
		return Collections.enumeration(this.headers.containsHeader(name) ? this.headers.get(name) : new LinkedList<>());
	}

	@Override
	public Enumeration<String> getHeaderNames() {
		return Collections.enumeration(this.headers.headerNames());
	}

	public void setHeader(String name, @Nullable String value) {
		this.headers.add(name, value);
	}

	public void addHeader(String name, @Nullable String value) {
		this.headers.add(name, value);
	}

	public void addHeaders(MultiValueMap<String, String> headers) {
		headers.forEach(this.headers::addAll);
	}

	public void setHeaders(MultiValueMap<String, String> headers) {
		this.headers.clear();
		this.addHeaders(headers);
	}

	@Override
	public int getIntHeader(String name) {
		List<String> header = this.headers.get(name);
		if (!CollectionUtils.isEmpty(header) && header.size() == 1) {
			Object value = header.get(0);
			if (value instanceof Number) {
				return ((Number) value).intValue();
			}
			else if (value instanceof String) {
				return Integer.parseInt((String) value);
			}
			else if (value != null) {
				throw new NumberFormatException("Value for header '" + name + "' is not a Number: " + value);
			}
			else {
				return -1;
			}
		}
		else {
			return -1;
		}
	}

	@Override
	public long getDateHeader(String name) {
		List<String> header = this.headers.get(name);
		if (!CollectionUtils.isEmpty(header) && header.size() == 1) {
			Object value = header.get(0);
			if (value instanceof Date) {
				return ((Date) value).getTime();
			}
			else if (value instanceof Number) {
				return ((Number) value).longValue();
			}
			else if (value instanceof String) {
				return parseDateHeader(name, (String) value);
			}
			else if (value != null) {
				throw new IllegalArgumentException(
						"Value for header '" + name + "' is not a Date, Number, or String: " + value);
			}
			else {
				return -1L;
			}
		}
		else {
			return -1;
		}
	}

	private long parseDateHeader(String name, String value) {
		for (String dateFormat : DATE_FORMATS) {
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat, Locale.US);
			simpleDateFormat.setTimeZone(GMT);
			try {
				return simpleDateFormat.parse(value).getTime();
			}
			catch (ParseException ex) {
				// ignore
			}
		}
		throw new IllegalArgumentException("Cannot parse date value '" + value + "' for '" + name + "' header");
	}

	public void setMethod(@Nullable String method) {
		this.method = method;
	}

	@Override
	@Nullable
	public String getMethod() {
		return this.method;
	}

	public void setPathInfo(@Nullable String pathInfo) {
		this.pathInfo = pathInfo;
	}

	@Override
	@Nullable
	public String getPathInfo() {
		return this.pathInfo;
	}

	@Override
	@Nullable
	public String getPathTranslated() {
		// return (this.pathInfo != null ? getRealPath(this.pathInfo) : null);
		return this.pathInfo;
	}

	public void setContextPath(String contextPath) {
		this.contextPath = contextPath;
	}

	@Override
	public String getContextPath() {
		return this.contextPath;
	}

	public void setQueryString(@Nullable String queryString) {
		this.queryString = queryString;
	}

	@Override
	@Nullable
	public String getQueryString() {
		return this.queryString;
	}

	public void setRemoteUser(@Nullable String remoteUser) {
		this.remoteUser = remoteUser;
	}

	@Override
	@Nullable
	public String getRemoteUser() {
		return this.remoteUser;
	}

	public void addUserRole(String role) {
		this.userRoles.add(role);
	}

	@Override
	public boolean isUserInRole(String role) {
		throw new UnsupportedOperationException();
	}

	public void setUserPrincipal(@Nullable Principal userPrincipal) {
		this.userPrincipal = userPrincipal;
	}

	@Override
	@Nullable
	public Principal getUserPrincipal() {
		return this.userPrincipal;
	}

	public void setRequestedSessionId(@Nullable String requestedSessionId) {
		this.requestedSessionId = requestedSessionId;
	}

	@Override
	@Nullable
	public String getRequestedSessionId() {
		return this.requestedSessionId;
	}

	public void setRequestURI(@Nullable String requestURI) {
		this.requestURI = requestURI;
	}

	@Override
	@Nullable
	public String getRequestURI() {
		return this.requestURI;
	}

	@Override
	public StringBuffer getRequestURL() {
		return new StringBuffer(this.requestURI);
	}

	public void setServletPath(String servletPath) {
		this.servletPath = servletPath;
	}

	@Override
	public String getServletPath() {
		return this.servletPath;
	}

	public void setSession(HttpSession session) {
		throw new UnsupportedOperationException();
	}

	@Override
	@Nullable
	public HttpSession getSession(boolean create) {
		if (this.session == null) {
			this.session = new ServerlessHttpSession(this.servletContext);
		}
		return this.session;
	}

	@Override
	@Nullable
	public HttpSession getSession() {
		return getSession(true);
	}

	@Override
	public String changeSessionId() {
		throw new UnsupportedOperationException();
	}

	public void setRequestedSessionIdValid(boolean requestedSessionIdValid) {
		this.requestedSessionIdValid = requestedSessionIdValid;
	}

	@Override
	public boolean isRequestedSessionIdValid() {
		return this.requestedSessionIdValid;
	}

	public void setRequestedSessionIdFromCookie(boolean requestedSessionIdFromCookie) {
		this.requestedSessionIdFromCookie = requestedSessionIdFromCookie;
	}

	@Override
	public boolean isRequestedSessionIdFromCookie() {
		return this.requestedSessionIdFromCookie;
	}

	public void setRequestedSessionIdFromURL(boolean requestedSessionIdFromURL) {
		this.requestedSessionIdFromURL = requestedSessionIdFromURL;
	}

	@Override
	public boolean isRequestedSessionIdFromURL() {
		return this.requestedSessionIdFromURL;
	}

	@Override
	public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void login(String username, String password) throws ServletException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void logout() throws ServletException {
		this.userPrincipal = null;
		this.remoteUser = null;
		this.authType = null;
	}

	public void addPart(Part part) {
		this.parts.add(part.getName(), part);
	}

	@Override
	@Nullable
	public Part getPart(String name) throws IOException, ServletException {
		return this.parts.getFirst(name);
	}

	@Override
	public Collection<Part> getParts() throws IOException, ServletException {
		List<Part> result = new LinkedList<>();
		for (List<Part> list : this.parts.values()) {
			result.addAll(list);
		}
		return result;
	}

	@Override
	public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getRequestId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getProtocolRequestId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ServletConnection getServletConnection() {
		// TODO Auto-generated method stub
		return null;
	}

}
