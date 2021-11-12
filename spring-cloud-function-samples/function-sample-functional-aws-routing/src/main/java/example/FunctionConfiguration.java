package example;

import java.util.function.Function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.MessageRoutingCallback;
import org.springframework.cloud.function.context.MessageRoutingCallback.FunctionRoutingResult;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.messaging.Message;

@SpringBootApplication
public class FunctionConfiguration implements ApplicationContextInitializer<GenericApplicationContext> {

	/*
	 * You need this main method or explicit <start-class>example.FunctionConfiguration</start-class>
	 * in the POM to ensure boot plug-in makes the correct entry
	 */
	public static void main(String[] args) {
		SpringApplication.run(FunctionConfiguration.class, args);
	}

	public Function<String, String> uppercase() {
		return value -> value.toUpperCase();
	}

	public Function<String, String> reverse() {
		return value -> new StringBuilder(value).reverse().toString();
	}

	public static class RoutingCallback implements MessageRoutingCallback {
		@Override
		public FunctionRoutingResult routingResult(Message<?> message) {
			String payload = new String((byte[]) message.getPayload());
			System.out.println("==> Will be routing based on payload: " + payload);
			return payload.contains("uppercase")
					? new FunctionRoutingResult("uppercase")
							: new FunctionRoutingResult("reverse");
		}
	}

	@Override
	public void initialize(GenericApplicationContext applicationContext) {
		System.out.println("==> Initializing");
		applicationContext.registerBean(MessageRoutingCallback.class,
				() -> new RoutingCallback());
		applicationContext.registerBean("uppercase", FunctionRegistration.class,
                () -> new FunctionRegistration<>(uppercase()).type(
                        FunctionType.from(String.class).to(String.class)));
		applicationContext.registerBean("reverse", FunctionRegistration.class,
                () -> new FunctionRegistration<>(reverse()).type(
                        FunctionType.from(String.class).to(String.class)));
	}
}
