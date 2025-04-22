/*
 * Copyright 2019-2025 the original author or authors.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.eclipse.aether.version.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * Resolves a {@link MavenResource} to
 * locate the artifact (uber jar) in a local Maven repository, downloading the latest update from a
 * remote repository if necessary.
 * <p>A set of default remote repos (Maven Central, Spring Snapshots, Spring Milestones) will be automatically added to
 * the head of the list of remote repos. If the default repo is already explicitly configured (exact match on the repo url)
 * then that particular default will be omitted. To skip the automatic default repos behavior altogether, set the
 * {@link MavenProperties#isIncludeDefaultRemoteRepos()} property to {@code false}.
 *
 * @author David Turanski
 * @author Mark Fisher
 * @author Marius Bogoevici
 * @author Ilayaperumal Gopinathan
 * @author Donovan Muller
 * @author Corneil du Plessis
 * @author Chris Bono
 */
class MavenArtifactResolver {

	private static final Logger logger = LoggerFactory.getLogger(MavenArtifactResolver.class);

	private static final String DEFAULT_CONTENT_TYPE = "default";

	private final RepositorySystem repositorySystem;

	private final MavenProperties properties;

	private final List<RemoteRepository> remoteRepositories = new LinkedList<>();

	private final Authentication proxyAuthentication;

	/**
	 * Create an instance using the provided properties.
	 *
	 * @param properties the properties for the maven repositories, proxies, and authentication
	 */
	MavenArtifactResolver(MavenProperties properties) {
		Assert.notNull(properties, "MavenProperties must not be null");
		Assert.notNull(properties.getLocalRepository(), "Local repository path cannot be null");
		this.properties = properties;
		if (logger.isDebugEnabled()) {
			logger.debug("Configured local repository: " + properties.getLocalRepository());
			logger.debug("Configured remote repositories: " + configuredRemoteRepositoriesDescription());
		}
		if (isProxyEnabled() && proxyHasCredentials()) {
			final String username = this.properties.getProxy().getAuth().getUsername();
			final String password = this.properties.getProxy().getAuth().getPassword();
			this.proxyAuthentication = newAuthentication(username, password);
		}
		else {
			this.proxyAuthentication = null;
		}
		File localRepository = new File(this.properties.getLocalRepository());
		if (!localRepository.exists()) {
			boolean created = localRepository.mkdirs();
			// May have been created by another thread after above check. Double check.
			Assert.isTrue(created || localRepository.exists(),
					"Unable to create directory for local repository: " + localRepository);
		}

		Map<String, String> defaultRepoUrlsToIds = defaultRemoteRepos();

		for (Map.Entry<String, MavenProperties.RemoteRepository> entry : this.properties.getRemoteRepositories()
				.entrySet()) {
			MavenProperties.RemoteRepository remoteRepository = entry.getValue();
			RemoteRepository.Builder remoteRepositoryBuilder = new RemoteRepository.Builder(
					entry.getKey(), DEFAULT_CONTENT_TYPE, remoteRepository.getUrl());
			// Update policies when set.
			if (remoteRepository.getPolicy() != null) {
				remoteRepositoryBuilder.setPolicy(new RepositoryPolicy(remoteRepository.getPolicy().isEnabled(),
						remoteRepository.getPolicy().getUpdatePolicy(),
						remoteRepository.getPolicy().getChecksumPolicy()));
			}
			if (remoteRepository.getReleasePolicy() != null) {
				remoteRepositoryBuilder
						.setReleasePolicy(new RepositoryPolicy(remoteRepository.getReleasePolicy().isEnabled(),
								remoteRepository.getReleasePolicy().getUpdatePolicy(),
								remoteRepository.getReleasePolicy().getChecksumPolicy()));
			}
			if (remoteRepository.getSnapshotPolicy() != null) {
				remoteRepositoryBuilder
						.setSnapshotPolicy(new RepositoryPolicy(remoteRepository.getSnapshotPolicy().isEnabled(),
								remoteRepository.getSnapshotPolicy().getUpdatePolicy(),
								remoteRepository.getSnapshotPolicy().getChecksumPolicy()));
			}
			if (remoteRepositoryHasCredentials(remoteRepository)) {
				final String username = remoteRepository.getAuth().getUsername();
				final String password = remoteRepository.getAuth().getPassword();
				remoteRepositoryBuilder.setAuthentication(newAuthentication(username, password));
			}
			// do not add default repo if explicitly configured
			defaultRepoUrlsToIds.remove(remoteRepository.getUrl());

			RemoteRepository repo = proxyRepoIfProxyEnabled(remoteRepositoryBuilder.build());
			this.remoteRepositories.add(repo);
		}

		if (!defaultRepoUrlsToIds.isEmpty() && this.properties.isIncludeDefaultRemoteRepos()) {
			List<RemoteRepository> defaultRepos = new ArrayList<>();
			defaultRepoUrlsToIds.forEach((url, id) -> {
				if (logger.isDebugEnabled()) {
					logger.debug("Adding {} ({}) to remote repositories list", id, url);
				}
				RemoteRepository defaultRepo = proxyRepoIfProxyEnabled(new RemoteRepository.Builder(id, DEFAULT_CONTENT_TYPE, url).build());
				defaultRepos.add(defaultRepo);
			});
			this.remoteRepositories.addAll(0, defaultRepos);
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Using remote repositories: {}", actualRemoteRepositoriesDescription());
		}
		this.repositorySystem = newRepositorySystem();
	}

	/**
	 * Gets the default repos to automatically add.
	 * @return map of default repos (repo url to repo id)
	 */
	protected Map<String, String> defaultRemoteRepos() {
		Map<String, String> defaultRepos = new LinkedHashMap<>();
		defaultRepos.put("https://repo.maven.apache.org/maven2", "mavenCentral-default");
		defaultRepos.put("https://repo.spring.io/snapshot", "springSnapshot-default");
		defaultRepos.put("https://repo.spring.io/milestone", "springMilestone-default");
		return defaultRepos;
	}

	private RemoteRepository proxyRepoIfProxyEnabled(RemoteRepository remoteRepo) {
		if (!isProxyEnabled()) {
			return remoteRepo;
		}
		Proxy proxy;
		MavenProperties.Proxy proxyProperties = this.properties.getProxy();
		if (this.proxyAuthentication != null) {
			proxy = new Proxy(
					proxyProperties.getProtocol(),
					proxyProperties.getHost(),
					proxyProperties.getPort(),
					this.proxyAuthentication);
		}
		else {
			// if proxy does not require authentication
			proxy = new Proxy(
					proxyProperties.getProtocol(),
					proxyProperties.getHost(),
					proxyProperties.getPort());
		}
		DefaultProxySelector proxySelector = new DefaultProxySelector();
		proxySelector.add(proxy, this.properties.getProxy().getNonProxyHosts());
		proxy = proxySelector.getProxy(remoteRepo);

		RemoteRepository.Builder remoteRepositoryBuilder = new RemoteRepository.Builder(remoteRepo);
		remoteRepositoryBuilder.setProxy(proxy);
		return remoteRepositoryBuilder.build();
	}

	/**
	 * Check if the proxy settings are provided.
	 *
	 * @return boolean true if the proxy settings are provided.
	 */
	private boolean isProxyEnabled() {
		return (this.properties.getProxy() != null &&
				this.properties.getProxy().getHost() != null &&
				this.properties.getProxy().getPort() > 0);
	}

	/**
	 * Check if the proxy setting has username/password set.
	 *
	 * @return boolean true if both the username/password are set
	 */
	private boolean proxyHasCredentials() {
		return (this.properties.getProxy() != null &&
				this.properties.getProxy().getAuth() != null &&
				this.properties.getProxy().getAuth().getUsername() != null &&
				this.properties.getProxy().getAuth().getPassword() != null);
	}

	/**
	 * Check if the {@link MavenProperties.RemoteRepository} setting has username/password set.
	 *
	 * @return boolean true if both the username/password are set
	 */
	private boolean remoteRepositoryHasCredentials(MavenProperties.RemoteRepository remoteRepository) {
		return remoteRepository != null &&
				remoteRepository.getAuth() != null &&
				remoteRepository.getAuth().getUsername() != null &&
				remoteRepository.getAuth().getPassword() != null;
	}

	/**
	 * Create an {@link Authentication} given a username/password.
	 *
	 * @param username the user
	 * @param password the password
	 * @return a configured {@link Authentication}
	 */
	private Authentication newAuthentication(final String username, final String password) {
		return new Authentication() {

			@Override
			public void fill(AuthenticationContext context, String key, Map<String, String> data) {
				context.put(AuthenticationContext.USERNAME, username);
				context.put(AuthenticationContext.PASSWORD, password);
			}

			@Override
			public void digest(AuthenticationDigest digest) {
				digest.update(AuthenticationContext.USERNAME, username,
						AuthenticationContext.PASSWORD, password);
			}
		};
	}

	DefaultRepositorySystemSession newRepositorySystemSession() {
		return this.newRepositorySystemSession(this.repositorySystem, this.properties.getLocalRepository());
	}

	/*
	 * Create a session to manage remote and local synchronization.
	 */
	private DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system, String localRepoPath) {
		DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
		LocalRepository localRepo = new LocalRepository(localRepoPath);
		session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
		session.setOffline(this.properties.isOffline());
		session.setUpdatePolicy(this.properties.getUpdatePolicy());
		session.setChecksumPolicy(this.properties.getChecksumPolicy());
		if (this.properties.isEnableRepositoryListener()) {
			session.setRepositoryListener(new LoggingRepositoryListener());
		}
		if (this.properties.getConnectTimeout() != null) {
			session.setConfigProperty(ConfigurationProperties.CONNECT_TIMEOUT, this.properties.getConnectTimeout());
		}
		if (this.properties.getRequestTimeout() != null) {
			session.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT, this.properties.getRequestTimeout());
		}
		if (isProxyEnabled()) {
			DefaultProxySelector proxySelector = new DefaultProxySelector();
			Proxy proxy = new Proxy(this.properties.getProxy().getProtocol(),
					this.properties.getProxy().getHost(),
					this.properties.getProxy().getPort(),
					this.proxyAuthentication);
			proxySelector.add(proxy, this.properties.getProxy().getNonProxyHosts());
			session.setProxySelector(proxySelector);
		}
		// wagon configs
		for (Entry<String, MavenProperties.RemoteRepository> entry : this.properties.getRemoteRepositories().entrySet()) {
			session.setConfigProperty("aether.connector.wagon.config." + entry.getKey(), entry.getValue().getWagon());
		}
		return session;
	}

	/*
	 * Aether's components implement {@link org.eclipse.aether.spi.locator.Service} to ease manual wiring.
	 * Using the prepopulated {@link DefaultServiceLocator}, we need to register the repository connector
	 * and transporter factories
	 */
	private RepositorySystem newRepositorySystem() {
		DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
		locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
		locator.addService(TransporterFactory.class, FileTransporterFactory.class);

		locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

		locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
			@Override
			public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
				throw new RuntimeException(exception);
			}
		});
		return locator.getService(RepositorySystem.class);
	}

	/**
	 * Gets the list of configured remote repositories.
	 * @return unmodifiable list of configured remote repositories.
	 */
	List<RemoteRepository> remoteRepositories() {
		return Collections.unmodifiableList(this.remoteRepositories);
	}

	private String actualRemoteRepositoriesDescription() {
		return this.remoteRepositories.stream().map((repo) -> String.format("%s (%s)", repo.getId(), repo.getUrl()))
				.collect(Collectors.joining(", ", "[", "]"));
	}

	private String configuredRemoteRepositoriesDescription() {
		return this.properties.getRemoteRepositories().entrySet().stream()
				.map((e) -> String.format("%s (%s)", e.getKey(), e.getValue().getUrl()))
				.collect(Collectors.joining(", ", "[", "]"));
	}

	List<String> getVersions(String coordinates) {
		Artifact artifact = new DefaultArtifact(coordinates);
		VersionRangeRequest rangeRequest = new VersionRangeRequest();
		rangeRequest.setArtifact(artifact);
		rangeRequest.setRepositories(this.remoteRepositories);
		try {
			VersionRangeResult versionResult = this.repositorySystem.resolveVersionRange(newRepositorySystemSession(), rangeRequest);
			List<String> versions = new ArrayList<>();
			for (Version version: versionResult.getVersions()) {
				versions.add(version.toString());
			}
			return versions;
		}
		catch (VersionRangeResolutionException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Resolve an artifact and return its location in the local repository. Aether performs the normal
	 * Maven resolution process ensuring that the latest update is cached to the local repository.
	 * In addition, if the {@code MavenProperties.resolvePom} flag is <code>true</code>,
	 * the POM is also resolved and cached.
	 * @param resource the {@link MavenResource} representing the artifact
	 * @return a {@link FileSystemResource} representing the resolved artifact in the local repository
	 * @throws IllegalStateException if the artifact does not exist or the resolution fails
	 */
	Resource resolve(MavenResource resource) {
		Assert.notNull(resource, "MavenResource must not be null");
		validateCoordinates(resource);
		RepositorySystemSession session = newRepositorySystemSession(this.repositorySystem, this.properties.getLocalRepository());
		try {
			List<ArtifactRequest> artifactRequests = new ArrayList<>(2);
			if (properties.isResolvePom()) {
				artifactRequests.add(new ArtifactRequest(toPomArtifact(resource), this.remoteRepositories, JavaScopes.RUNTIME));
			}
			artifactRequests.add(new ArtifactRequest(toJarArtifact(resource), this.remoteRepositories, JavaScopes.RUNTIME));
			List<ArtifactResult> results = this.repositorySystem.resolveArtifacts(session, artifactRequests);
			return toResource(results.get(results.size() - 1));
		}
		catch (ArtifactResolutionException ex) {
			String errorMsg = String.format("Failed to resolve %s using remote repo(s): %s",
					resource, actualRemoteRepositoriesDescription());
			throw new IllegalStateException(errorMsg, ex);
		}
	}

	private void validateCoordinates(MavenResource resource) {
		Assert.hasText(resource.getGroupId(), "groupId must not be blank.");
		Assert.hasText(resource.getArtifactId(), "artifactId must not be blank.");
		Assert.hasText(resource.getExtension(), "extension must not be blank.");
		Assert.hasText(resource.getVersion(), "version must not be blank.");
	}

	public FileSystemResource toResource(ArtifactResult resolvedArtifact) {
		return new FileSystemResource(resolvedArtifact.getArtifact().getFile());
	}

	private Artifact toJarArtifact(MavenResource resource) {
		return toArtifact(resource, resource.getExtension());
	}

	private Artifact toPomArtifact(MavenResource resource) {
		return toArtifact(resource, "pom");
	}

	private Artifact toArtifact(MavenResource resource, String extension) {
		return new DefaultArtifact(resource.getGroupId(),
				resource.getArtifactId(),
				resource.getClassifier() != null ? resource.getClassifier() : "",
				extension,
				resource.getVersion());
	}
}
