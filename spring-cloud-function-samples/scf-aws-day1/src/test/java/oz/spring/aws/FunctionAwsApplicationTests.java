package oz.spring.aws;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.function.adapter.aws.FunctionInvoker;

class FunctionAwsApplicationTests {
	
	@Test
	void validateFunctionInvocation() throws Exception {
		System.setProperty("MAIN_CLASS", FunctionAwsApplication.class.getName());
		FunctionInvoker invoker = new FunctionInvoker("uppercase");
		
		InputStream targetStream = new ByteArrayInputStream("\"hello aws\"".getBytes());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		invoker.handleRequest(targetStream, output, null);
		String result = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(result).isEqualTo("\"HELLO AWS\"");
	}

}
