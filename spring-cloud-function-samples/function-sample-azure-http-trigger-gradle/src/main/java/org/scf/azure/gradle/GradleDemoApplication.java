package org.scf.azure.gradle;

import java.util.Optional;
import java.util.function.Function;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.adapter.azure.AzureFunctionUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;

@SpringBootApplication
public class GradleDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(GradleDemoApplication.class, args);
	}

	/**
	 * Plain Spring bean (not Spring Cloud Functions!)
	 */
	@Autowired
	private Function<Message<String>, String> uppercase;

	@FunctionName("bean")
	public String plainBean(
			@HttpTrigger(name = "req", methods = { HttpMethod.GET,
					HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
			ExecutionContext context) {

		// Inject the ExecutionContext as Message header
		Message<String> enhancedRequest = (Message<String>) AzureFunctionUtil.enhanceInputIfNecessary(
				request.getBody().get(),
				context);

		return this.uppercase.apply(enhancedRequest);
	}

	@Bean
	public Function<Message<String>, String> uppercase() {
		return message -> {
			ExecutionContext context = (ExecutionContext) message.getHeaders().get(AzureFunctionUtil.EXECUTION_CONTEXT);

			String updatedPayload = message.getPayload().toUpperCase();

			context.getLogger().info("Azure Test: " + updatedPayload);

			return message.getPayload().toUpperCase();
		};
	}

}
