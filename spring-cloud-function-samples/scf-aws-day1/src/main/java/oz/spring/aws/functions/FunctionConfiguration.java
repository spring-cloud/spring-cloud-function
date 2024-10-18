package oz.spring.aws.functions;

import java.util.Locale;
import java.util.function.Function;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FunctionConfiguration {

	@Bean
	public Function<String, String> uppercase() {
		return value -> value.toUpperCase(Locale.ROOT);
	}
}
