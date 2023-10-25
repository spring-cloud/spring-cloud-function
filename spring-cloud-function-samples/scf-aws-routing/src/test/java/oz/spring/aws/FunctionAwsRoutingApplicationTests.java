package oz.spring.aws;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.function.adapter.aws.FunctionInvoker;
import org.springframework.messaging.support.MessageBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

public class FunctionAwsRoutingApplicationTests {
	
	@Test
	void validateFunctionRouting() throws Exception {
		System.setProperty("MAIN_CLASS", FunctionAwsApplication.class.getName());
		FunctionInvoker invoker = new FunctionInvoker();
		
		String jsonInput = this.generateJsonInput("lowercase");
		
		InputStream targetStream = new ByteArrayInputStream(jsonInput.getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);
		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).isEqualTo("\"hello aws routing\"");
		
		jsonInput = this.generateJsonInput("reverse");
		
		targetStream = new ByteArrayInputStream(jsonInput.getBytes());
		output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);
		result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).isEqualTo("\"gnituoR SWA olleH\"");
	}

	
	private String generateJsonInput(String functionDefinition) throws Exception {
		return new ObjectMapper().writeValueAsString(
				MessageBuilder.withPayload("Hello AWS Routing").setHeader("spring.cloud.function.definition", functionDefinition).build());
	}
}