import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Subprocess entry point for {@code GcfJarLauncherTests}. Loads
 * {@code GcfJarLauncher} from a Spring Boot fat JAR through a child
 * {@link URLClassLoader}, mirroring how the Google Cloud Functions framework
 * loads the deployed function JAR, and reports whether the {@code nested:}
 * URL protocol is registered before and after the launcher is constructed
 * (GH-1336).
 */
public class ProtocolCheck {

	public static void main(String[] args) {
		File functionJar = new File(args[0]);
		try {
			new URL("nested:/dev/null");
			System.out.println("PROTOCOL_ALREADY_REGISTERED");
		}
		catch (MalformedURLException ex) {
			System.out.println("PROTOCOL_NOT_REGISTERED: " + ex.getMessage());
		}
		try (URLClassLoader child = new URLClassLoader(new URL[] { toUrl(functionJar) },
				ClassLoader.getSystemClassLoader())) {
			Class<?> launcher = Class.forName("org.springframework.cloud.function.adapter.gcp.GcfJarLauncher",
					true, child);
			launcher.getConstructor().newInstance();
			System.out.println("GCF_JAR_LAUNCHER_SUCCEEDED");
		}
		catch (Throwable ex) {
			System.out.println("GCF_JAR_LAUNCHER_FAILED: " + ex);
			ex.printStackTrace(System.out);
		}
		try {
			new URL("nested:/dev/null");
			System.out.println("PROTOCOL_NOW_REGISTERED");
		}
		catch (MalformedURLException ex) {
			System.out.println("PROTOCOL_STILL_NOT_REGISTERED: " + ex.getMessage());
		}
	}

	private static URL toUrl(File file) {
		try {
			return file.toURI().toURL();
		}
		catch (MalformedURLException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
