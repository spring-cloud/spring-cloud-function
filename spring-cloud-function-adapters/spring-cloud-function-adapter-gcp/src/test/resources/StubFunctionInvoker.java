package org.springframework.cloud.function.adapter.gcp;

/**
 * Minimal stand-in for the real {@code FunctionInvoker} used by
 * {@code GcfJarLauncherTests}. The real class would bootstrap the entire
 * Spring application in its constructor, which is not needed to verify that
 * {@code GcfJarLauncher} can load its delegate from a nested JAR.
 */
public class FunctionInvoker {

	public FunctionInvoker() {
	}

}
