/*
 * Copyright 2019-present the original author or authors.
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

package org.springframework.cloud.function.deployer.utils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Configuration Properties for Maven.
 *
 * @author Ilayaperumal Gopinathan
 * @author Eric Bottard
 * @author Mark Fisher
 * @author Donovan Muller
 */
public class MavenProperties {

	/**
	 * Default file path to a locally available maven repository.
	 */
	private static String DEFAULT_LOCAL_REPO = System.getProperty("user.home") +
			File.separator + ".m2" + File.separator + "repository";

	/**
	 * Whether default remote repositories should be automatically included in the list of remote repositories.
	 */
	private boolean includeDefaultRemoteRepos = true;

	/**
	 * File path to a locally available maven repository, where artifacts will be downloaded.
	 */
	private String localRepository = DEFAULT_LOCAL_REPO;

	/**
	 * Locations of remote maven repositories from which artifacts will be downloaded, if not available locally.
	 */
	private Map<String, RemoteRepository> remoteRepositories = new TreeMap<>();

	/**
	 * Whether the resolver should operate in offline mode.
	 */
	private boolean offline;

	/**
	 * Proxy configuration properties.
	 */
	private Proxy proxy;

	/**
	 * The connect timeout. If <code>null</code>, the underlying default will be used.
	 */
	private Integer connectTimeout;

	/**
	 * The request timeout. If <code>null</code>, the underlying default will be used.
	 */
	private Integer requestTimeout;

	/**
	 * In addition to resolving the JAR artifact, if true, resolve the POM artifact.
	 * This is consistent with the way that Maven resolves artifacts.
	 */
	private boolean resolvePom;

	private String updatePolicy;

	private String checksumPolicy;

	/**
	 * Add the ConsoleRepositoryListener to the session for debugging of artifact resolution.
	 */
	private boolean enableRepositoryListener = false;

	boolean isIncludeDefaultRemoteRepos() {
		return includeDefaultRemoteRepos;
	}

	void setIncludeDefaultRemoteRepos(boolean includeDefaultRemoteRepos) {
		this.includeDefaultRemoteRepos = includeDefaultRemoteRepos;
	}

	/**
	 * Use maven wagon based transport for http based artifacts.
	 */
	private boolean useWagon;

	public void setUseWagon(boolean useWagon) {
		this.useWagon = useWagon;
	}

	public boolean isUseWagon() {
		return useWagon;
	}

	public boolean isEnableRepositoryListener() {
		return enableRepositoryListener;
	}

	public void setEnableRepositoryListener(boolean enableRepositoryListener) {
		this.enableRepositoryListener = enableRepositoryListener;
	}

	public String getUpdatePolicy() {
		return updatePolicy;
	}

	public void setUpdatePolicy(String updatePolicy) {
		this.updatePolicy = updatePolicy;
	}

	public String getChecksumPolicy() {
		return checksumPolicy;
	}

	public void setChecksumPolicy(String checksumPolicy) {
		this.checksumPolicy = checksumPolicy;
	}

	public Map<String, RemoteRepository> getRemoteRepositories() {
		return remoteRepositories;
	}

	public void setRemoteRepositories(final Map<String, RemoteRepository> remoteRepositories) {
		this.remoteRepositories = new TreeMap<>(remoteRepositories);
	}

	public void setLocalRepository(String localRepository) {
		this.localRepository = localRepository;
	}

	public String getLocalRepository() {
		return localRepository;
	}

	public boolean isOffline() {
		return offline;
	}

	public void setOffline(Boolean offline) {
		this.offline = offline;
	}

	public Integer getConnectTimeout() {
		return this.connectTimeout;
	}

	public void setConnectTimeout(Integer connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public Integer getRequestTimeout() {
		return this.requestTimeout;
	}

	public void setRequestTimeout(Integer requestTimeout) {
		this.requestTimeout = requestTimeout;
	}

	public Proxy getProxy() {
		return this.proxy;
	}

	public void setProxy(Proxy proxy) {
		this.proxy = proxy;
	}

	public boolean isResolvePom() {
		return resolvePom;
	}

	public void setResolvePom(final boolean resolvePom) {
		this.resolvePom = resolvePom;
	}

	public static class Proxy {

		/**
		 * Protocol to use for proxy settings.
		 */
		private String protocol = "http";

		/**
		 * Host for the proxy.
		 */
		private String host;

		/**
		 * Port for the proxy.
		 */
		private int port;

		/**
		 * List of non proxy hosts.
		 */
		private String nonProxyHosts;

		private Authentication auth;

		public String getProtocol() {
			return this.protocol;
		}

		public void setProtocol(String protocol) {
			this.protocol = protocol;
		}

		public String getHost() {
			return this.host;
		}

		public void setHost(String host) {
			this.host = host;
		}

		public int getPort() {
			return this.port;
		}

		public void setPort(int port) {
			this.port = port;
		}

		public String getNonProxyHosts() {
			return this.nonProxyHosts;
		}

		public void setNonProxyHosts(String nonProxyHosts) {
			this.nonProxyHosts = nonProxyHosts;
		}

		public Authentication getAuth() {
			return this.auth;
		}

		public void setAuth(Authentication auth) {
			this.auth = auth;
		}
	}

	public enum WagonHttpMethod {
		// directly maps to http methods in org.apache.maven.wagon.shared.http.HttpConfiguration
		/**
		 * All methods.
		 */
		all,
		/**
		 * GET method.
		 */
		get,
		/**
		 * PUT method.
		 */
		put,
		/**
		 * HEAD method.
		 */
		head;
	}

	public static class WagonHttpMethodProperties {
		// directly maps to settings in org.apache.maven.wagon.shared.http.HttpMethodConfiguration
		private boolean usePreemptive;
		private boolean useDefaultHeaders;
		private Integer connectionTimeout;
		private Integer readTimeout;
		private Map<String, String> headers = new HashMap<>();
		private Map<String, String> params = new HashMap<>();

		public boolean isUsePreemptive() {
			return usePreemptive;
		}

		public void setUsePreemptive(boolean usePreemptive) {
			this.usePreemptive = usePreemptive;
		}

		public boolean isUseDefaultHeaders() {
			return useDefaultHeaders;
		}

		public void setUseDefaultHeaders(boolean useDefaultHeaders) {
			this.useDefaultHeaders = useDefaultHeaders;
		}

		public Integer getConnectionTimeout() {
			return connectionTimeout;
		}

		public void setConnectionTimeout(Integer connectionTimeout) {
			this.connectionTimeout = connectionTimeout;
		}

		public Integer getReadTimeout() {
			return readTimeout;
		}

		public void setReadTimeout(Integer readTimeout) {
			this.readTimeout = readTimeout;
		}

		public Map<String, String> getHeaders() {
			return headers;
		}

		public void setHeaders(Map<String, String> headers) {
			this.headers = headers;
		}

		public Map<String, String> getParams() {
			return params;
		}

		public void setParams(Map<String, String> params) {
			this.params = params;
		}
	}

	public static class Wagon {

		private Map<WagonHttpMethod, WagonHttpMethodProperties> http = new HashMap<>();

		public Map<WagonHttpMethod, WagonHttpMethodProperties> getHttp() {
			return http;
		}

		public void setHttp(Map<WagonHttpMethod, WagonHttpMethodProperties> http) {
			this.http = http;
		}
	}

	public static class RemoteRepository {

		/**
		 * URL of the remote maven repository. E.g. https://my.repo.com
		 */
		private String url;

		private Authentication auth;

		private RepositoryPolicy policy;

		private RepositoryPolicy snapshotPolicy;

		private RepositoryPolicy releasePolicy;

		private Wagon wagon = new Wagon();

		public RemoteRepository() {
		}

		public RemoteRepository(final String url) {
			this.url = url;
		}

		public RemoteRepository(final String url, final Authentication auth) {
			this.url = url;
			this.auth = auth;
		}

		public Wagon getWagon() {
			return wagon;
		}

		public void setWagon(Wagon wagon) {
			this.wagon = wagon;
		}

		public String getUrl() {
			return url;
		}

		public void setUrl(final String url) {
			this.url = url;
		}

		public Authentication getAuth() {
			return auth;
		}

		public void setAuth(final Authentication auth) {
			this.auth = auth;
		}

		public RepositoryPolicy getPolicy() {
			return policy;
		}

		public void setPolicy(RepositoryPolicy policy) {
			this.policy = policy;
		}

		public RepositoryPolicy getSnapshotPolicy() {
			return snapshotPolicy;
		}

		public void setSnapshotPolicy(RepositoryPolicy snapshotPolicy) {
			this.snapshotPolicy = snapshotPolicy;
		}

		public RepositoryPolicy getReleasePolicy() {
			return releasePolicy;
		}

		public void setReleasePolicy(RepositoryPolicy releasePolicy) {
			this.releasePolicy = releasePolicy;
		}
	}

	public static class RepositoryPolicy {

		private boolean enabled = true;

		private String updatePolicy = "always";

		private String checksumPolicy = "warn";

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getUpdatePolicy() {
			return updatePolicy;
		}

		public void setUpdatePolicy(String updatePolicy) {
			this.updatePolicy = updatePolicy;
		}

		public String getChecksumPolicy() {
			return checksumPolicy;
		}

		public void setChecksumPolicy(String checksumPolicy) {
			this.checksumPolicy = checksumPolicy;
		}

	}

	public static class Authentication {

		private String username;

		private String password;

		public Authentication() {
		}

		public Authentication(String username, String password) {
			this.username = username;
			this.password = password;
		}

		public String getUsername() {
			return this.username;
		}

		public void setUsername(String username) {
			this.username = username;
		}

		public String getPassword() {
			return this.password;
		}

		public void setPassword(String password) {
			this.password = password;
		}

	}
}

