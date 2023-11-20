/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.cloud.function.gradle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.jvm.tasks.Jar;
import org.springframework.boot.experimental.gradle.ThinLauncherPlugin;

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer;

public class AwsPackagingPlugin implements Plugin<Project> {
	@Override
	public void apply(Project project) {
		System.out.println("=====> Hello Plugin");
		
		project.getPlugins().apply("java");
		project.getPlugins().apply(ThinLauncherPlugin.class);
		project.getPlugins().apply("com.github.johnrengelman.shadow");
		project.getPlugins().apply("io.spring.dependency-management");
		
		
		TaskContainer taskContainer = project.getTasks();
		taskContainer.forEach(System.out::println);
		JavaPluginExtension javaExtension = (JavaPluginExtension) project.getExtensions().findByName("java");
		javaExtension.setSourceCompatibility("17");
		
		//.setProperty("sourceCompatibility", "17")
		
		List<Task> dependentTasks = List.of(taskContainer.findByName("thinJar"), taskContainer.findByName("shadowJar"));
		taskContainer.findByName("assemble").dependsOn(dependentTasks);
		
		//taskContainer.findByName("java").setProperty("sourceCompatibility", "17");
		
		this.configureShadowJarTask(project);
	}
	
	private void configureShadowJarTask(Project project) {
		ShadowJar shadowJar = (ShadowJar) project.getTasks().findByName("shadowJar");
		shadowJar.setProperty("archiveClassifier", "aws");
		
		shadowJar.manifest(new Action<Manifest>() {
			@Override
			public void execute(Manifest mfst) {
				mfst.from(((Jar) project.getTasks().findByName("thinJar")).getManifest());
			}
		});
		
		shadowJar.mustRunAfter(project.getTasks().findByName("thinJar"));
		shadowJar.mergeServiceFiles();

		shadowJar.append("META-INF/spring.handlers");
		shadowJar.append("META-INF/spring.schemas");
		shadowJar.append("META-INF/spring.tooling");
		shadowJar.append("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
		shadowJar.append("META-INF/spring/org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration.imports");
		PropertiesFileTransformer xfmr = new PropertiesFileTransformer();
		xfmr.setPaths(Collections.singletonList("META-INF/spring.factories"));
		xfmr.setMergeStrategy("append");
		shadowJar.transform(xfmr);
	}
}
