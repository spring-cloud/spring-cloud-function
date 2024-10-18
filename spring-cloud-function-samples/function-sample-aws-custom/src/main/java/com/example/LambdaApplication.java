package com.example;

import java.util.Locale;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionalSpringApplication;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;

@SpringBootApplication
public class LambdaApplication
		implements ApplicationContextInitializer<GenericApplicationContext> {

	private static Log logger = LogFactory.getLog(LambdaApplication.class);

	public Function<String, String> uppercase() {
		return value -> {
			logger.info("Processing: " + value);
			if (value.equals("error")) {
				throw new IllegalArgumentException("Intentional");
			}
			return value.toUpperCase(Locale.ROOT);
		};
	}

	public static void main(String[] args) {
		FunctionalSpringApplication.run(LambdaApplication.class, args);
	}

	@Override
	public void initialize(GenericApplicationContext context) {
		context.registerBean("uppercase", FunctionRegistration.class,
				() -> new FunctionRegistration<>(uppercase()).type(FunctionTypeUtils.functionType(String.class, String.class)));
	}
}
