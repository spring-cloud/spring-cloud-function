import org.springframework.cloud.function.adapter.gcp.GcfJarLauncher;
import org.springframework.cloud.function.adapter.gcp.GcfJarLauncherTests;

/**
 * Subprocess entry point for {@link GcfJarLauncherTests}. Compiled and executed
 * at runtime to verify that the {@code nested:} protocol handler is registered
 * after {@code GcfJarLauncher} construction (GH-1336).
 *
 * @author Roman Akentev
 */
public class ProtocolCheck {

	public static void main(String[] args) throws Exception {
		try {
			new java.net.URL("nested:/dev/null");
			System.out.println(GcfJarLauncherTests.PROTOCOL_ALREADY_REGISTERED);
		}
		catch (java.net.MalformedURLException e) {
			System.out.println(GcfJarLauncherTests.PROTOCOL_NOT_REGISTERED + ": " + e.getMessage());
		}
		try {
			new GcfJarLauncher();
			System.out.println(GcfJarLauncherTests.GCF_JAR_LAUNCHER_SUCCEEDED);
		}
		catch (Exception e) {
			System.out.println(GcfJarLauncherTests.GCF_JAR_LAUNCHER_FAILED + ": "
					+ e.getClass().getName() + ": " + e.getMessage());
			e.printStackTrace(System.out);
		}
		try {
			new java.net.URL("nested:/dev/null");
			System.out.println(GcfJarLauncherTests.PROTOCOL_NOW_REGISTERED);
		}
		catch (java.net.MalformedURLException e) {
			System.out.println(GcfJarLauncherTests.PROTOCOL_STILL_NOT_REGISTERED + ": " + e.getMessage());
		}
	}

}
