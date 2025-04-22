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

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Corneil du Plessis
 */
public class LoggingRepositoryListener extends AbstractRepositoryListener {

	private static final Logger logger = LoggerFactory.getLogger(LoggingRepositoryListener.class);

	public void artifactDeployed(RepositoryEvent event) {
		println("artifactDeployed", event.getArtifact() + " to " + event.getRepository());
	}

	public void artifactDeploying(RepositoryEvent event) {
		println("artifactDeploying", event.getArtifact() + " to " + event.getRepository());
	}

	public void artifactDescriptorInvalid(RepositoryEvent event) {
		println("artifactDescriptorInvalid", "for " + event.getArtifact() + ": " + event.getException().getMessage());
	}

	public void artifactDescriptorMissing(RepositoryEvent event) {
		println("artifactDescriptorMissing", "for " + event.getArtifact());
	}

	public void artifactInstalled(RepositoryEvent event) {
		println("artifactInstalled", event.getArtifact() + " to " + event.getFile());
	}

	public void artifactInstalling(RepositoryEvent event) {
		println("artifactInstalling", event.getArtifact() + " to " + event.getFile());
	}

	public void artifactResolved(RepositoryEvent event) {
		println("artifactResolved", event.getArtifact() + " from " + event.getRepository());
	}

	public void artifactDownloading(RepositoryEvent event) {
		println("artifactDownloading", event.getArtifact() + " from " + event.getRepository());
	}

	public void artifactDownloaded(RepositoryEvent event) {
		println("artifactDownloaded", event.getArtifact() + " from " + event.getRepository());
	}

	public void artifactResolving(RepositoryEvent event) {
		println("artifactResolving", event.getArtifact().toString());
	}

	public void metadataDeployed(RepositoryEvent event) {
		println("metadataDeployed", event.getMetadata() + " to " + event.getRepository());
	}

	public void metadataDeploying(RepositoryEvent event) {
		println("metadataDeploying", event.getMetadata() + " to " + event.getRepository());
	}

	public void metadataInstalled(RepositoryEvent event) {
		println("metadataInstalled", event.getMetadata() + " to " + event.getFile());
	}

	public void metadataInstalling(RepositoryEvent event) {
		println("metadataInstalling", event.getMetadata() + " to " + event.getFile());
	}

	public void metadataInvalid(RepositoryEvent event) {
		println("metadataInvalid", event.getMetadata().toString());
	}

	public void metadataResolved(RepositoryEvent event) {
		println("metadataResolved", event.getMetadata() + " from " + event.getRepository());
	}

	public void metadataResolving(RepositoryEvent event) {
		println("metadataResolving", event.getMetadata() + " from " + event.getRepository());
	}

	private void println(String event, String message) {
		logger.info("Aether Repository - " + event + ": " + message);
	}
}
