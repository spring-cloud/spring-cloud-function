package com.example.demo;

import java.util.function.Function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class NativeUppercaseApplication {

	public static void main(String[] args) {
		SpringApplication.run(NativeUppercaseApplication.class, args);
	}
	
	@Bean
	public Function<String, String> uppercase() {
		return v -> {
			System.out.println("Uppercasing " + v);
			return v.toUpperCase();
		};
	}

}
