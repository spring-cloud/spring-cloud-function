/*
 * Copyright 2016-2017 the original author or authors.
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
package org.springframework.cloud.function.deployer;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.cloud.deployer.resource.maven.MavenResourceLoader;
import org.springframework.cloud.deployer.resource.support.DelegatingResourceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;

/**
 * @author Dave Syer
 *
 */
@Configuration
@ConditionalOnProperty(prefix = "function.deployer", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties
@Import(FunctionCreatorConfiguration.class)
public class FunctionDeployerConfiguration {

	@Bean
	@ConfigurationProperties("maven")
	public MavenProperties mavenProperties() {
		return new MavenProperties();
	}

	@Bean
	@ConfigurationProperties("function")
	public FunctionProperties functionProperties() {
		return new FunctionProperties();
	}

	@Bean
	@ConditionalOnMissingBean(DelegatingResourceLoader.class)
	public DelegatingResourceLoader delegatingResourceLoader(
			MavenProperties mavenProperties) {
		Map<String, ResourceLoader> loaders = new HashMap<>();
		loaders.put(MavenResource.URI_SCHEME, new MavenResourceLoader(mavenProperties));
		return new DelegatingResourceLoader(loaders);
	}

}
