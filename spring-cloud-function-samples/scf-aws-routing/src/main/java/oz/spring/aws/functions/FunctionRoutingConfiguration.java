package oz.spring.aws.functions;

import java.util.function.Function;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FunctionRoutingConfiguration {

	@Bean
	public Function<String, String> lowercase() {
		return value -> value.toLowerCase();
	}
	
	@Bean
	public Function<String, String> reverse() {
		return value -> new StringBuilder(value).reverse().toString();
	}
}
