/*
 * Copyright 2018-2020 the original author or authors.
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

package org.springframework.cloud.function.adapter.gcp.layout;

import java.io.IOException;

import org.springframework.boot.loader.tools.CustomLoaderLayout;
import org.springframework.boot.loader.tools.Layouts;
import org.springframework.boot.loader.tools.LoaderClassesWriter;
import org.springframework.cloud.function.adapter.gcp.GcfJarLauncher;

/**
 * A custom JAR Layout that writes GCF adapter classes to the top-level of the output JAR
 * for deploying to GCF.
 *
 * @author Ray Tsang
 * @author Daniel Zou
 */
public class GcfJarLayout extends Layouts.Jar implements CustomLoaderLayout {

	private static final String LAUNCHER_NAME = GcfJarLauncher.class.getCanonicalName();

	@Override
	public String getLauncherClassName() {
		return LAUNCHER_NAME;
	}

	@Override
	public boolean isExecutable() {
		return false;
	}

	@Override
	public void writeLoadedClasses(LoaderClassesWriter writer) throws IOException {
		writer.writeLoaderClasses();

		String jarName = LAUNCHER_NAME.replaceAll("\\.", "/") + ".class";
		writer.writeEntry(
			jarName, GcfJarLauncher.class.getResourceAsStream("/" + jarName));
	}
}
