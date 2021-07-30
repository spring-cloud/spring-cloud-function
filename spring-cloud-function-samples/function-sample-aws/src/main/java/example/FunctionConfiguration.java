package example;

import java.util.function.Function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class FunctionConfiguration {

	/*
	 * You need this main method or explicit <start-class>example.FunctionConfiguration</start-class>
	 * in the POM to ensure boot plug-in makes the correct entry
	 */
	public static void main(String[] args) {
		SpringApplication.run(FunctionConfiguration.class, args);
	}

	@Bean
	public Function<String, String> uppercase() {
		return value -> value.toUpperCase();
	}

	@Bean
	public Function<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> uppercaseApiGateway() {
		return value -> {
			APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
			response.setStatusCode(404);
			response.setBody("Resource not found");
			return response;
		};
	}
}
